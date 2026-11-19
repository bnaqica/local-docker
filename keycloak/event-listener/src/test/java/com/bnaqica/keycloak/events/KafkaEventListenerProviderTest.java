package com.bnaqica.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.mockito.ArgumentCaptor;

/** Keycloak's session and transaction are mocked; the Kafka side uses kafka-clients' own MockProducer. */
class KafkaEventListenerProviderTest {

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final KeycloakTransactionManager transactions = mock(KeycloakTransactionManager.class);
    private final List<String[]> published = new ArrayList<>();
    private final EventPublisher publisher = (key, json) -> published.add(new String[] {key, json});
    private final KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, publisher);

    @BeforeEach
    void setUp() {
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn("crm-internal");
        when(realms.getRealm("realm-uuid")).thenReturn(realm);
        when(session.realms()).thenReturn(realms);
        when(session.getTransactionManager()).thenReturn(transactions);
    }

    private static Event login() {
        Event event = new Event();
        event.setId("e1");
        event.setTime(1L);
        event.setRealmId("realm-uuid");
        event.setType(EventType.LOGIN);
        event.setUserId("u1");
        return event;
    }

    private KeycloakTransaction enlisted() {
        var captor = ArgumentCaptor.forClass(KeycloakTransaction.class);
        verify(transactions).enlistAfterCompletion(captor.capture());
        return captor.getValue();
    }

    @Test
    void userEventIsPublishedOnlyAfterCommit() {
        provider.onEvent(login());
        assertThat(published).isEmpty();

        KeycloakTransaction tx = enlisted();
        tx.begin();
        tx.commit();

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()[0]).isEqualTo("u1");
        assertThat(published.getFirst()[1]).contains("\"realm\":\"crm-internal\"").contains("\"type\":\"LOGIN\"");
    }

    @Test
    void nothingIsPublishedWhenTheTransactionRollsBack() {
        provider.onEvent(login());

        KeycloakTransaction tx = enlisted();
        tx.begin();
        tx.rollback();

        assertThat(published).isEmpty();
    }

    @Test
    void adminEventIsPublishedAfterCommit() {
        AdminEvent event = new AdminEvent();
        event.setId("a1");
        event.setRealmId("realm-uuid");
        event.setOperationType(OperationType.CREATE);
        event.setResourceType(ResourceType.USER);
        event.setResourcePath("users/u2");

        provider.onEvent(event, true);
        KeycloakTransaction tx = enlisted();
        tx.begin();
        tx.commit();

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()[1]).contains("\"kind\":\"ADMIN\"").contains("\"realm\":\"crm-internal\"");
    }

    @Test
    void unknownRealmFallsBackToItsId() {
        when(session.realms().getRealm(any())).thenReturn(null);

        provider.onEvent(login());
        KeycloakTransaction tx = enlisted();
        tx.begin();
        tx.commit();

        assertThat(published.getFirst()[1]).contains("\"realm\":\"realm-uuid\"");
    }

    @Test
    void kafkaFailureNeverReachesKeycloak() {
        var producer = new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
        var kafka = new KafkaEventPublisher(producer, "keycloak-events");

        assertThatCode(() -> {
                    kafka.publish("u1", "{}");
                    producer.errorNext(new RuntimeException("broker down"));
                })
                .doesNotThrowAnyException();
    }

    @Test
    void kafkaPublisherSendsToTheTopicWithTheKey() {
        var producer = new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());

        new KafkaEventPublisher(producer, "keycloak-events").publish("u1", "{\"id\":\"e1\"}");

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().getFirst().topic()).isEqualTo("keycloak-events");
        assertThat(producer.history().getFirst().key()).isEqualTo("u1");
        assertThat(producer.history().getFirst().value()).isEqualTo("{\"id\":\"e1\"}");
    }
}
