package com.bnaqica.keycloak.events;

import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Publishes every user and admin event of the realms that list this listener. A message is sent only after the
 * Keycloak transaction that produced the event commits, so consumers never see events of rolled-back changes.
 */
public class KafkaEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;
    private final EventPublisher publisher;

    public KafkaEventListenerProvider(KeycloakSession session, EventPublisher publisher) {
        this.session = session;
        this.publisher = publisher;
    }

    @Override
    public void onEvent(Event event) {
        publishAfterCommit(EventMessages.userEvent(event, realmName(event.getRealmId())));
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        publishAfterCommit(EventMessages.adminEvent(event, realmName(event.getRealmId())));
    }

    private void publishAfterCommit(Map<String, Object> message) {
        String key = EventMessages.key(message);
        String json = EventMessages.toJson(message);
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                publisher.publish(key, json);
            }

            @Override
            protected void rollbackImpl() {
                // the change didn't happen, so neither did its event
            }
        });
    }

    private String realmName(String realmId) {
        RealmModel realm = realmId == null ? null : session.realms().getRealm(realmId);
        return realm != null ? realm.getName() : realmId;
    }

    @Override
    public void close() {
        // The publisher is shared and owned by the factory
    }
}
