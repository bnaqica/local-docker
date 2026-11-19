package com.bnaqica.keycloak.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.util.JsonSerialization;

/**
 * Turns Keycloak events into the JSON messages published to {@code keycloak-events}. The shapes are the contract
 * read by the CRM user management service (its {@code docs/events.md} and {@code docs/audit.md}):
 *
 * <ul>
 *   <li>user events: {@code {id, time, realm, type, userId, error, details}}
 *   <li>admin events: {@code {id, time, realm, kind: "ADMIN", operationType, resourceType, resourcePath,
 *       authDetails: {userId, ipAddress, clientId}, representation, error}}
 * </ul>
 *
 * <p>{@code realm} is the realm's name, not Keycloak's internal realm id. Null fields are kept as {@code null}.
 */
public final class EventMessages {

    /** Keycloak's own mapper, but keeping null fields so every message has the same keys. */
    private static final ObjectMapper JSON =
            JsonSerialization.mapper.copy().setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private EventMessages() {}

    public static Map<String, Object> userEvent(Event event, String realmName) {
        Map<String, String> details = new LinkedHashMap<>();
        if (event.getDetails() != null) {
            details.putAll(event.getDetails());
        }
        putIfPresent(details, "ipAddress", event.getIpAddress());
        putIfPresent(details, "clientId", event.getClientId());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", event.getId());
        message.put("time", event.getTime());
        message.put("realm", realmName);
        message.put("type", event.getType() == null ? null : event.getType().name());
        message.put("userId", event.getUserId());
        message.put("error", event.getError());
        message.put("details", details);
        return message;
    }

    public static Map<String, Object> adminEvent(AdminEvent event, String realmName) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", event.getId());
        message.put("time", event.getTime());
        message.put("realm", realmName);
        message.put("kind", "ADMIN");
        message.put("operationType", event.getOperationType() == null ? null : event.getOperationType().name());
        message.put("resourceType", event.getResourceTypeAsString());
        message.put("resourcePath", event.getResourcePath());
        message.put("authDetails", authDetails(event.getAuthDetails()));
        message.put("representation", event.getRepresentation());
        message.put("error", event.getError());
        return message;
    }

    /** Kafka key: the user for user events (so one user's events stay in order), otherwise the realm. */
    public static String key(Map<String, Object> message) {
        Object userId = message.get("userId");
        return userId != null ? userId.toString() : String.valueOf(message.get("realm"));
    }

    public static String toJson(Map<String, Object> message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (IOException ex) {
            throw new UncheckedIOException("Couldn't serialise Keycloak event " + message.get("id"), ex);
        }
    }

    private static Map<String, String> authDetails(AuthDetails auth) {
        Map<String, String> details = new LinkedHashMap<>();
        if (auth != null) {
            putIfPresent(details, "userId", auth.getUserId());
            putIfPresent(details, "ipAddress", auth.getIpAddress());
            putIfPresent(details, "clientId", auth.getClientId());
        }
        return details;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
