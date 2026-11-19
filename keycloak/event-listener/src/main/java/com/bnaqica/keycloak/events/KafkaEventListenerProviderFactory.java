package com.bnaqica.keycloak.events;

import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Event listener {@code kafka}: add it to a realm's event listeners to publish that realm's user and admin events
 * to Kafka. One producer is shared by every request.
 *
 * <p>Settings, from Keycloak options ({@code spi-events-listener-kafka-<name>} in {@code keycloak.conf}, or
 * {@code KC_SPI_EVENTS_LISTENER_KAFKA_<NAME>}) or else the plain environment variables:
 *
 * <ul>
 *   <li>{@code bootstrap-servers} / {@code KAFKA_BOOTSTRAP_SERVERS} (required), for example {@code kafka:29092}
 *   <li>{@code topic} / {@code KEYCLOAK_EVENTS_TOPIC}, default {@code keycloak-events}
 * </ul>
 */
public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String ID = "kafka";

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProviderFactory.class);

    private String bootstrapServers;
    private String topic;
    private KafkaEventPublisher publisher;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new KafkaEventListenerProvider(session, publisher);
    }

    @Override
    public void init(Config.Scope config) {
        bootstrapServers = config.get("bootstrap-servers", System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
        topic = config.get("topic", envOr("KEYCLOAK_EVENTS_TOPIC", "keycloak-events"));
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException(
                    "Event listener 'kafka' needs KAFKA_BOOTSTRAP_SERVERS (or the spi-events-listener-kafka-bootstrap-servers option)");
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.CLIENT_ID_CONFIG, "keycloak-event-listener",
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.LINGER_MS_CONFIG, 5,
                // A login must not hang if Kafka is down: give up waiting for the broker quickly
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        // Serializer instances, not class names: Keycloak's class loader can't see this jar's classes by name
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            publisher = new KafkaEventPublisher(
                    new KafkaProducer<>(config, new StringSerializer(), new StringSerializer()), topic);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        LOG.infof("Publishing Keycloak events to Kafka topic %s at %s", topic, bootstrapServers);
    }

    @Override
    public void close() {
        if (publisher != null) {
            publisher.close();
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
