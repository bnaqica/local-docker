package com.bnaqica.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.util.JsonSerialization;

/** The message shapes the user management service reads from keycloak-events (its docs/events.md and docs/audit.md). */
class EventMessagesTest {

    private static Event login() {
        Event event = new Event();
        event.setId("e1");
        event.setTime(1_762_855_200_000L);
        event.setRealmId("realm-uuid");
        event.setType(EventType.LOGIN);
        event.setUserId("7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11");
        event.setClientId("crm-app");
        event.setIpAddress("10.0.0.5");
        Map<String, String> details = new HashMap<>();
        details.put("username", "ana@corp.example");
        event.setDetails(details);
        return event;
    }

    @Test
    void userEventCarriesTheContractFieldsWithTheRealmName() {
        Map<String, Object> message = EventMessages.userEvent(login(), "crm-internal");

        assertThat(message)
                .containsEntry("id", "e1")
                .containsEntry("time", 1_762_855_200_000L)
                .containsEntry("realm", "crm-internal")
                .containsEntry("type", "LOGIN")
                .containsEntry("userId", "7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11")
                .containsEntry("error", null);
        assertThat(message).doesNotContainKey("kind");
    }

    @Test
    void userEventDetailsIncludeIpAddressAndClient() {
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) EventMessages.userEvent(login(), "crm-internal").get("details");

        assertThat(details)
                .containsEntry("username", "ana@corp.example")
                .containsEntry("ipAddress", "10.0.0.5")
                .containsEntry("clientId", "crm-app");
    }

    @Test
    void failedUserEventCarriesItsError() {
        Event event = login();
        event.setType(EventType.LOGIN_ERROR);
        event.setError("invalid_user_credentials");

        assertThat(EventMessages.userEvent(event, "crm-internal"))
                .containsEntry("type", "LOGIN_ERROR")
                .containsEntry("error", "invalid_user_credentials");
    }

    @Test
    void userEventWithoutDetailsStillHasADetailsObject() {
        Event event = login();
        event.setDetails(null);
        event.setIpAddress(null);
        event.setClientId(null);

        assertThat(EventMessages.userEvent(event, "portal")).containsEntry("details", Map.of());
    }

    @Test
    void adminEventIsMarkedAdminWithItsResourceAndActor() {
        AdminEvent event = new AdminEvent();
        event.setId("a1");
        event.setTime(1_762_855_200_000L);
        event.setRealmId("realm-uuid");
        event.setOperationType(OperationType.UPDATE);
        event.setResourceType(ResourceType.USER);
        event.setResourcePath("users/7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11");
        event.setRepresentation("{\"enabled\":false}");
        AuthDetails auth = new AuthDetails();
        auth.setUserId("admin-sub");
        auth.setIpAddress("10.0.0.9");
        auth.setRealmId("master-uuid");
        auth.setClientId("ums-internal");
        event.setAuthDetails(auth);

        Map<String, Object> message = EventMessages.adminEvent(event, "crm-internal");

        assertThat(message)
                .containsEntry("id", "a1")
                .containsEntry("time", 1_762_855_200_000L)
                .containsEntry("realm", "crm-internal")
                .containsEntry("kind", "ADMIN")
                .containsEntry("operationType", "UPDATE")
                .containsEntry("resourceType", "USER")
                .containsEntry("resourcePath", "users/7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11")
                .containsEntry("representation", "{\"enabled\":false}")
                .containsEntry("error", null)
                .containsEntry("authDetails", Map.of("userId", "admin-sub", "ipAddress", "10.0.0.9", "clientId", "ums-internal"));
    }

    @Test
    void messagesAreKeyedByUserOrRealm() {
        assertThat(EventMessages.key(EventMessages.userEvent(login(), "crm-internal")))
                .isEqualTo("7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11");
        Event noUser = login();
        noUser.setUserId(null);
        assertThat(EventMessages.key(EventMessages.userEvent(noUser, "crm-internal"))).isEqualTo("crm-internal");
    }

    @Test
    void serialisesToJsonWithNullFieldsKept() throws Exception {
        String json = EventMessages.toJson(EventMessages.userEvent(login(), "crm-internal"));

        assertThat(JsonSerialization.readValue(json, Map.class)).containsKey("error").containsEntry("type", "LOGIN");
    }
}
