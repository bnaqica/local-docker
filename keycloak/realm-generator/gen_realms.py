"""Generates the three CRM realm-import files.

Local:  python3 gen_realms.py ../realms
Tests:  python3 gen_realms.py <service>/src/test/resources/keycloak --test   (adds test users and a password-grant client)

default-scopes.json holds Keycloak 26.8.1's built-in client scopes; crm-internal needs them because it defines custom ones.
"""
import json, sys, os
out = sys.argv[1]; test = '--test' in sys.argv
os.makedirs(out, exist_ok=True)

def pkce_client(client_id, name, port, audience=None):
    c = {"clientId": client_id, "name": name, "enabled": True, "publicClient": True,
         "standardFlowEnabled": True, "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
         "serviceAccountsEnabled": False,
         "redirectUris": [f"http://localhost:{port}/*"], "webOrigins": [f"http://localhost:{port}"],
         "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": f"http://localhost:{port}/*"}}
    if audience:
        c["protocolMappers"] = [{"name": f"audience-{audience}", "protocol": "openid-connect",
            "protocolMapper": "oidc-audience-mapper",
            "config": {"included.client.audience": audience, "access.token.claim": "true", "id.token.claim": "false"}}]
    return c

def service_client(client_id, desc, secret, scopes=()):
    return {"clientId": client_id, "description": desc, "enabled": True, "publicClient": False,
            "clientAuthenticatorType": "client-secret", "secret": secret,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
            "serviceAccountsEnabled": True,
            "defaultClientScopes": ["roles", "basic", "service_account", *scopes], "optionalClientScopes": []}

def service_account_user(client_id, realm_mgmt_roles):
    return {"username": f"service-account-{client_id}", "enabled": True, "serviceAccountClientId": client_id,
            "clientRoles": {"realm-management": realm_mgmt_roles}}

def scope(name, desc):
    return {"name": name, "description": desc, "protocol": "openid-connect",
            "attributes": {"include.in.token.scope": "true", "display.on.consent.screen": "false"}}

_defaults = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "default-scopes.json")))
for _s in _defaults:
    _s.pop("id", None)
    for _m in _s.get("protocolMappers", []):
        _m.pop("id", None)
DEFAULT_SCOPES = ["profile", "email", "roles", "web-origins", "acr", "basic", "role_list", "saml_organization"]
OPTIONAL_SCOPES = ["address", "phone", "offline_access", "microprofile-jwt", "organization"]
# The UMS also manages the client roles that hold permissions and roles (crm-api, portal-app); manage-clients is
# broader than that (every client in the realm) until fine-grained admin permissions narrow it
UMS_ROLES = ["manage-users", "view-users", "query-groups", "view-clients", "manage-clients"]
smtp = {"host": "mailpit", "port": "1025", "from": "no-reply@crm.local", "fromDisplayName": "CRM Demo"}
common = {"enabled": True, "sslRequired": "none"  # local development over plain HTTP
         , "loginWithEmailAllowed": True,
          "duplicateEmailsAllowed": False, "accessTokenLifespan": 300, "bruteForceProtected": True,
          "permanentLockout": True, "maxTemporaryLockouts": 3, "failureFactor": 5,
          "waitIncrementSeconds": 60, "maxFailureWaitSeconds": 900, "eventsEnabled": True,
          "eventsExpiration": 2592000, "adminEventsEnabled": True,
          # Representations let the central audit record before/after; it strips secrets before storing
          "adminEventsDetailsEnabled": True,
          "smtpServer": smtp}
pw_policy = "length(12) and notUsername and notEmail"

def test_user(username, email, realm_roles=(), password="Test-password-123"):
    return {"username": username, "email": email, "emailVerified": True, "enabled": True,
            "firstName": "Test", "lastName": username, "realmRoles": list(realm_roles),
            "credentials": [{"type": "password", "value": password, "temporary": False}]}

def test_client():
    # Test-only client so tests can get user tokens with a password grant
    return {"clientId": "test-client", "enabled": True, "publicClient": True,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": True}

# crm-admin
admin = {**common, "realm": "crm-admin", "displayName": "CRM Admin",
         "registrationAllowed": False, "resetPasswordAllowed": False, "passwordPolicy": pw_policy,
         "ssoSessionIdleTimeout": 900, "ssoSessionMaxLifespan": 28800,
         "roles": {"realm": [{"name": "admin", "description": "UMS administrator"}]},
         "clients": [pkce_client("admin-app", "Admin UI", 5175),
                     # The UMS reads admin accounts to protect the last admin, and creates approved ones
                     service_client("ums-admin", "UMS admin-account guardrails in crm-admin", "ums-admin-local-secret")],
         "users": []}
if test:
    admin["clients"].append(test_client())
    admin["users"] = [test_user("test-admin", "test-admin@crm.local", ["admin"]),
                      test_user("test-admin-2", "test-admin-2@crm.local", ["admin"]),
                      test_user("test-not-admin", "test-not-admin@crm.local")]
else:
    admin["users"] = [{"username": "admin.local", "email": "admin.local@crm.local", "emailVerified": True,
                       "enabled": True, "firstName": "Local", "lastName": "Admin", "realmRoles": ["admin"],
                       "requiredActions": ["CONFIGURE_TOTP"],
                       "credentials": [{"type": "password", "value": "Local-admin-123", "temporary": False}]}]
admin["users"].append(service_account_user("ums-admin", ["manage-users", "view-users", "query-users"]))

# crm-internal
internal = {**common, "realm": "crm-internal", "displayName": "CRM",
            # Username follows the email, so a renamed user can't still sign in as, or block, the old address
            "registrationEmailAsUsername": True,
            "registrationAllowed": False, "resetPasswordAllowed": True, "passwordPolicy": pw_policy,
            "actionTokenGeneratedByUserLifespan": 1800,
            # Listing custom scopes stops Keycloak creating its built-in ones, so they are included here
            "defaultDefaultClientScopes": DEFAULT_SCOPES, "defaultOptionalClientScopes": OPTIONAL_SCOPES,
            "clientScopes": _defaults + [scope("ums:team-scope", "Read team scope from the UMS"),
                             scope("ums:catalog", "Register permissions in the UMS catalog"),
                             scope("ums:promote", "Promote external users through the UMS")],
            "clients": [pkce_client("crm-app", "CRM UI", 5173, audience="crm-api"),
                        {"clientId": "crm-api", "description": "Resource server; holds CRM permissions as client roles",
                         "enabled": True, "publicClient": False, "bearerOnly": False, "standardFlowEnabled": False,
                         "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False},
                        service_client("ums-internal", "UMS user management in crm-internal", "ums-internal-local-secret"),
                        service_client("crm-promoter", "Calls the UMS promote API", "crm-promoter-local-secret", ["ums:promote"]),
                        service_client("core-service", "core-service calls to the UMS", "core-service-local-secret", ["ums:team-scope", "ums:catalog"])],
            "users": [service_account_user("ums-internal", UMS_ROLES)]}
if test:
    internal["clients"].append(test_client())
    internal["users"].append(test_user("test-employee", "test-employee@corp.example"))

def portal_role(name, desc):
    return {"name": name, "description": desc, "composite": True, "composites": {"client": {"portal-app": [name]}}}

# portal
portal = {**common, "realm": "portal", "displayName": "Customer Portal",
          "registrationAllowed": True, "registrationEmailAsUsername": True, "verifyEmail": True,
          "resetPasswordAllowed": True, "passwordPolicy": pw_policy, "actionTokenGeneratedByUserLifespan": 1800,
          # The portal service reads these realm roles; their permissions are portal-app client roles that the UMS
          # manages as composites of the portal-app role of the same name
          "roles": {"realm": [portal_role("external-starter", "Self-registered portal user"),
                              portal_role("external-standard", "Promoted or invited portal user")],
                    "client": {"portal-app": [{"name": "external-starter", "description": "Self-registered portal user"},
                                              {"name": "external-standard", "description": "Promoted or invited portal user"}]}},
          "clients": [pkce_client("portal-app", "Customer Portal", 5174),
                      service_client("ums-portal", "UMS user management in portal", "ums-portal-local-secret")],
          # view-realm (read-only): removing a realm role from a user (promotion) needs the role's id
          "users": [service_account_user("ums-portal", UMS_ROLES + ["view-realm"])]}
if test:
    portal["clients"].append(test_client())
    portal["users"].append(test_user("test-portal@acme.example", "test-portal@acme.example", ["external-standard"]))

# Local realms publish their events to Kafka through the event listener in ../event-listener.
# Test realms leave it out: the services' Testcontainers Keycloak doesn't load that provider.
if not test:
    for r in (admin, internal, portal):
        r["eventsListeners"] = ["jboss-logging", "kafka"]

for r in (admin, internal, portal):
    with open(os.path.join(out, f"{r['realm']}-realm.json"), "w") as f:
        json.dump(r, f, indent=2); f.write("\n")
print("wrote", out)
