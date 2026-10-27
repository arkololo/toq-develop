[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $container = (& docker compose ps --quiet postgres).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'PostgreSQL local indisponible.' }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $destination = Join-Path $root ".local/backups/$stamp"
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    foreach ($database in @('toq_user','toq_recipe','toq_nutrition','toq_planner','toq_social','keycloak')) {
        $inside = "/tmp/toq-backup-$database.dump"
        & docker compose exec -T postgres pg_dump -U toq_admin -d $database -Fc -f $inside
        if ($LASTEXITCODE -ne 0) { throw "Sauvegarde impossible : $database" }
        & docker cp "${container}:$inside" (Join-Path $destination "$database.dump")
        if ($LASTEXITCODE -ne 0) { throw "Copie impossible : $database" }
    }
    Write-Output "Sauvegarde locale créée : $destination"
} finally { Pop-Location }
