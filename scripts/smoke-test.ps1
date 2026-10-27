[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    try {
        $checks = @(
            @('http://localhost:8080/', 200),
            @('http://localhost:8080/healthz', 200),
            @('http://localhost:8080/actuator/health', 404),
            @('http://localhost:8080/internal/v1/test', 404),
            @('http://localhost:8080/api/v1/users/actuator/health', 404),
            @('http://localhost:8080/api/v1/unknown', 404),
            @('http://localhost:8080/api/v1/users/me', 401),
            @('http://localhost:8080/api/v1/assistant/status', 401),
            @('http://localhost:8080/api/v1/recipes', 401),
            @('http://localhost:8080/api/v1/nutrition/foods', 401),
            @('http://localhost:8080/api/v1/plans/2026-09-21', 401),
            @('http://localhost:8080/api/v1/social/publications', 401),
            @('http://localhost:8081/realms/master/.well-known/openid-configuration', 200)
        )
        foreach ($check in $checks) {
            $response = $client.GetAsync([string]$check[0]).GetAwaiter().GetResult()
            try {
                if ([int]$response.StatusCode -ne [int]$check[1]) {
                    throw "HTTP inattendu pour $($check[0]) : $([int]$response.StatusCode), attendu $($check[1])."
                }
                Write-Host "OK HTTP $($check[1]) $($check[0])"
            } finally { $response.Dispose() }
        }
    } finally { $client.Dispose() }

    foreach ($service in @('postgres', 'keycloak', 'user-service', 'recipe-service', 'nutrition-service', 'planner-service', 'social-service', 'frontend-edge')) {
        $containerId = & docker compose ps --quiet $service
        if ($LASTEXITCODE -ne 0 -or -not $containerId) { throw "Conteneur manquant : $service" }
        $container = (& docker inspect $containerId | ConvertFrom-Json)[0]
        if ($container.State.Health.Status -ne 'healthy') { throw "$service n'est pas healthy." }
        foreach ($port in $container.NetworkSettings.Ports.PSObject.Properties) {
            foreach ($binding in @($port.Value)) {
                if ($binding -and $binding.HostIp -ne '127.0.0.1') { throw "Port non limité à localhost : $service" }
                if ($binding -and $service -notin @('frontend-edge', 'keycloak')) { throw "Port interne publié : $service" }
            }
        }
        Write-Host "OK santé et isolation réseau : $service"
    }
    Get-Content -LiteralPath (Join-Path $PSScriptRoot 'check-database-isolation.sql') -Raw |
        & docker compose exec -T postgres psql --username=toq_admin --dbname=postgres --no-psqlrc --set=ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Isolation PostgreSQL incorrecte.' }
    Write-Host 'Smoke tests locaux réussis.'
} finally { Pop-Location }
