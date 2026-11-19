package com.bnaqica.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.keycloak.util.JsonSerialization;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

/** The built provider jar in a real Keycloak (same image as local-docker), publishing to a real Kafka. */
@Testcontainers
class KeycloakToKafkaIT {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2")
            .withNetwork(NETWORK)
            .withListener("kafka:19092");

    @Container
    static final GenericContainer<?> KEYCLOAK = new KeycloakWithMasterOverHttp()
            .withNetwork(NETWORK)
            .dependsOn(KAFKA)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(System.getProperty("providerJar"))),
                    "/opt/keycloak/providers/keycloak-kafka-event-listener.jar")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("events-test-realm.json"), "/opt/keycloak/data/import/events-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080, 9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    private static Map<?, ?> postForm(String url, String form) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonSerialization.readValue(response.body(), Map.class);
    }

    private static String token(String realm, String client, String user, String password) throws Exception {
        String form = "grant_type=password&client_id=" + client + "&username=" + user + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return (String) postForm(baseUrl() + "/realms/" + realm + "/protocol/openid-connect/token", form)
                .get("access_token");
    }

    /** Every message on keycloak-events so far, parsed. */
    private static List<Map<?, ?>> messages() throws IOException {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<Map<?, ?>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("keycloak-events"));
            long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    found.add(JsonSerialization.readValue(record.value(), Map.class));
                }
            }
        }
        return found;
    }

    private static List<Map<?, ?>> awaitMessages(java.util.function.Predicate<Map<?, ?>> match) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            List<Map<?, ?>> matching = messages().stream().filter(match).toList();
            if (!matching.isEmpty()) {
                return matching;
            }
        }
        throw new AssertionError("No matching message on keycloak-events");
    }

    @Test
    void loginIsPublishedWithTheRealmNameAndUser() throws Exception {
        token("events-test", "test-client", "ana", "Test-password-123");

        Map<?, ?> login = awaitMessages(m -> "LOGIN".equals(m.get("type"))).getFirst();

        assertThat(login.get("realm")).isEqualTo("events-test");
        assertThat(login.get("userId")).asString().isNotBlank();
        assertThat(login.get("id")).asString().isNotBlank();
        assertThat(login.get("time")).isInstanceOf(Number.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) login.get("details");
        assertThat(details).containsEntry("username", "ana").containsKey("ipAddress");
    }

    @Test
    void failedLoginIsPublishedWithItsError() throws Exception {
        String form = "grant_type=password&client_id=test-client&username=ana&password=wrong";
        HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/realms/events-test/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        Map<?, ?> error = awaitMessages(m -> "LOGIN_ERROR".equals(m.get("type"))).getFirst();

        assertThat(error.get("error")).isEqualTo("invalid_user_credentials");
    }

    @Test
    void adminChangeIsPublishedAsAnAdminEvent() throws Exception {
        String admin = token("master", "admin-cli", "admin", "admin");
        HttpResponse<String> created = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/admin/realms/events-test/users"))
                        .header("Authorization", "Bearer " + admin)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"enabled\":true}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);

        Map<?, ?> event = awaitMessages(m -> "ADMIN".equals(m.get("kind"))
                        && "CREATE".equals(m.get("operationType"))
                        && "USER".equals(m.get("resourceType")))
                .getFirst();

        assertThat(event.get("realm")).isEqualTo("events-test");
        assertThat(event.get("resourcePath")).asString().startsWith("users/");
        assertThat(event.get("representation")).asString().contains("bob@example.com");
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) event.get("authDetails");
        assertThat(auth).containsKey("userId");
    }

    /** The master realm demands HTTPS from outside the container; the test uses its admin over plain HTTP. */
    static class KeycloakWithMasterOverHttp extends GenericContainer<KeycloakWithMasterOverHttp> {
        KeycloakWithMasterOverHttp() {
            super("quay.io/keycloak/keycloak:26.8.1");
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse info) {
            try {
                String kcadm = "/opt/keycloak/bin/kcadm.sh";
                execInContainer(kcadm, "config", "credentials", "--server", "http://localhost:8080", "--realm",
                        "master", "--user", "admin", "--password", "admin");
                execInContainer(kcadm, "update", "realms/master", "-s", "sslRequired=NONE");
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
    }
}
