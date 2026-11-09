"""Validate the resolved HTTPS Compose model without starting any container."""
import json
import ipaddress
from pathlib import Path
import subprocess
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
result = subprocess.run(
    ['docker', 'compose', '-f', 'compose.https.yaml', 'config', '--format', 'json'],
    cwd=root, capture_output=True, text=True,
)
if result.returncode:
    raise SystemExit('Invalid Compose configuration. Run docker compose -f compose.https.yaml config --quiet to diagnose.')

services = json.loads(result.stdout)['services']
public = services['tls-edge']['environment']
for key, port in [('TOQ_PUBLIC_FRONTEND_URL', 8080), ('TOQ_PUBLIC_KEYCLOAK_URL', 8081)]:
    value = public[key]
    url = urlsplit(value)
    if (url.scheme != 'https' or not url.hostname or url.port != port
            or url.username or url.password or url.path or url.query or url.fragment):
        raise SystemExit(f'{key} must be an HTTPS origin on port {port}, without credentials, path or trailing slash.')

try:
    ipaddress.ip_address(urlsplit(public['TOQ_PUBLIC_FRONTEND_URL']).hostname)
except ValueError:
    pass
else:
    assert public['TOQ_TLS_SERVER_NAME'] == urlsplit(public['TOQ_PUBLIC_FRONTEND_URL']).hostname, 'Set TOQ_TLS_SERVER_NAME to the public IP for clients without SNI'

assert not services['frontend-edge'].get('ports'), 'Frontend HTTP port is still published'
assert not services['keycloak'].get('ports'), 'Keycloak HTTP port is still published'
auth = public['TOQ_PUBLIC_KEYCLOAK_URL']
front = public['TOQ_PUBLIC_FRONTEND_URL']
kc = services['keycloak']['environment']
assert kc['KC_HOSTNAME'] == auth, 'Public hostname mismatch'
assert kc['TOQ_PUBLIC_FRONTEND_URL'] == front, 'Realm import origin mismatch'
assert kc['KC_PROXY_HEADERS'] == 'xforwarded', 'Missing trusted TLS proxy headers'
assert services['frontend-edge']['build']['args']['VITE_KEYCLOAK_URL'] == auth, 'Frontend/CSP URL mismatch'
for name in ['user', 'recipe', 'nutrition', 'planner', 'social']:
    service = services[name + '-service']
    assert not service.get('ports'), 'Internal API port is published'
    assert service['environment']['TOQ_OIDC_ISSUER_URI'] == auth + '/realms/TOQ', 'Issuer mismatch'
    assert service['environment']['TOQ_OIDC_JWK_SET_URI'] == 'http://keycloak:8080/realms/TOQ/protocol/openid-connect/certs', 'Internal JWKS changed'
assert not services['postgres'].get('ports'), 'PostgreSQL port is published'
assert {str(p['published']) for p in services['tls-edge']['ports']} == {'8080', '8081'}, 'Incorrect TLS ports'
print('HTTPS Compose configuration OK: TLS ports, origins, issuer, CSP build input and internal-only HTTP.')
