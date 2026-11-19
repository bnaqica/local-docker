package com.bnaqica.keycloak.events;

/** Sends one event message. Implementations must never throw into Keycloak's request handling. */
@FunctionalInterface
public interface EventPublisher {

    void publish(String key, String json);
}
