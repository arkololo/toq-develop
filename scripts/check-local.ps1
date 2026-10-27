[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    & .\mvnw.cmd --batch-mode --no-transfer-progress verify
    if ($LASTEXITCODE -ne 0) { throw 'Les tests backend ont échoué.' }
    Push-Location (Join-Path $taskRoot 'frontend')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Installation frontend en échec.' }
        foreach ($taskScript in @('lint', 'test', 'build')) {
            & npm.cmd run $taskScript
            if ($LASTEXITCODE -ne 0) { throw "Vérification frontend en échec : $taskScript" }
        }
    } finally { Pop-Location }
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Configuration Compose invalide ; exécuter setup-local.ps1 auparavant.' }
    Write-Host 'Vérifications locales réussies.'
} finally { Pop-Location }

