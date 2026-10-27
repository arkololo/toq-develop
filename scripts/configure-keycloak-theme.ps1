param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run setup-local.ps1 first.' }
$line = Get-Content -LiteralPath $envPath | Where-Object { $_ -match '^KEYCLOAK_ADMIN_PASSWORD=' } | Select-Object -First 1
if (-not $line) { throw 'KEYCLOAK_ADMIN_PASSWORD is missing.' }
$secret = $line.Substring('KEYCLOAK_ADMIN_PASSWORD='.Length)
if ([string]::IsNullOrWhiteSpace($secret)) { throw 'KEYCLOAK_ADMIN_PASSWORD is empty.' }
$token = Invoke-RestMethod -Method Post -Uri "$BaseUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ grant_type = 'password'; client_id = 'admin-cli'; username = 'toq-admin'; password = $secret }
$settings = @{ loginTheme = 'toq'; internationalizationEnabled = $true; supportedLocales = @('fr', 'en'); defaultLocale = 'fr' } | ConvertTo-Json -Compress
Invoke-WebRequest -Method Put -Uri "$BaseUrl/admin/realms/TOQ" `
    -Headers @{ Authorization = "Bearer $($token.access_token)" } `
    -ContentType 'application/json' -Body $settings -UseBasicParsing | Out-Null
Write-Output 'TOQ login/register theme configured in the existing Keycloak realm.'
