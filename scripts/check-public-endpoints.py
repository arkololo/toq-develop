"""Read-only checks from a client PC; no credentials and no deployment."""
import argparse
import json
import ssl
from urllib.error import HTTPError
from urllib.request import urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--frontend', default='http://localhost:8080')
parser.add_argument('--keycloak', default='http://localhost:8081')
parser.add_argument('--ca-file', help='Public root CA PEM for private HTTPS; certificate verification stays enabled')
args = parser.parse_args()
front, auth = args.frontend.rstrip('/'), args.keycloak.rstrip('/')
context = ssl.create_default_context(cafile=args.ca_file)

def get(url, expected):
    try:
        response = urlopen(url, timeout=15, context=context)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f'{url}: {response.status}, expected {expected}'
        body, headers = response.read(), response.headers
    print(f'OK {expected} {url}')
    return body, headers

_, headers = get(front + '/', 200)
csp = headers.get('Content-Security-Policy', '')
assert f"connect-src 'self' {auth};" in csp, 'CSP connect-src does not match Keycloak'
assert f'frame-src {auth};' in csp, 'CSP frame-src does not match Keycloak'
for path, status in [('/healthz', 200), ('/api/v1/unknown', 404),
                     ('/api/v1/users/actuator/health', 404),
                     ('/internal/v1/test', 404)]:
    get(front + path, status)
for path in ['users/me', 'assistant/status', 'recipes', 'ingredients',
             'nutrition/foods', 'plans/2026-11-09', 'shopping-lists', 'social/publications']:
    get(front + '/api/v1/' + path, 401)
body, _ = get(auth + '/realms/TOQ/.well-known/openid-configuration', 200)
discovery = json.loads(body)
assert discovery['issuer'] == auth + '/realms/TOQ', 'Incorrect public issuer'
for key in ['authorization_endpoint', 'token_endpoint', 'end_session_endpoint']:
    assert discovery[key].startswith(auth + '/'), f'Incorrect public {key}'
print('Public endpoints OK. Browser login, CORS and token refresh still require manual testing.')
