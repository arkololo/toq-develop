[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
$templatePath = Join-Path $projectRoot '.env.example'
$secretKeys = @(
    'POSTGRES_PASSWORD',
    'TOQ_USER_DB_PASSWORD',
    'TOQ_RECIPE_DB_PASSWORD',
    'TOQ_NUTRITION_DB_PASSWORD',
    'TOQ_PLANNER_DB_PASSWORD',
    'TOQ_SOCIAL_DB_PASSWORD',
    'KEYCLOAK_DB_PASSWORD',
    'KEYCLOAK_ADMIN_PASSWORD'
)

if (Test-Path -LiteralPath $envPath) {
    $content = [System.IO.File]::ReadAllText($envPath)
} else {
    $content = [System.IO.File]::ReadAllText($templatePath)
}

$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    foreach ($key in $secretKeys) {
        $pattern = '(?m)^' + [regex]::Escape($key) + '=([^\r\n]*)'
        $matchesForKey = [regex]::Matches($content, $pattern)
        if ($matchesForKey.Count -gt 1) {
            throw "Duplicate key $key in .env. Resolve it without changing initialized database passwords."
        }
        if ($matchesForKey.Count -eq 1 -and $matchesForKey[0].Groups[1].Value.Trim().Length -gt 0) {
            continue
        }
        $bytes = New-Object byte[] 32
        $random.GetBytes($bytes)
        $value = [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        $line = $key + '=' + $value
        if ($matchesForKey.Count -eq 1) {
            $content = [regex]::Replace($content, $pattern, $line)
        } else {
            $content = $content.TrimEnd() + "`n" + $line + "`n"
        }
    }
} finally {
    $random.Dispose()
}

# Do not emit credentials to the console or pass them to another process.
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($envPath, $content.Replace("`r`n", "`n"), $utf8)
if ($env:OS -eq 'Windows_NT') {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemIdentity = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')
    # Load and modify the DACL only. Set-Acl on a fresh FileSecurity object can
    # attempt to write the SACL and require SeSecurityPrivilege on a second run.
    $accessSection = [System.Security.AccessControl.AccessControlSections]::Access
    $fileInfo = Get-Item -LiteralPath $envPath
    if ($PSVersionTable.PSVersion.Major -ge 6) {
        $acl = [System.IO.FileSystemAclExtensions]::GetAccessControl($fileInfo, $accessSection)
    } else {
        $acl = [System.IO.File]::GetAccessControl($envPath, $accessSection)
    }
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) { $acl.RemoveAccessRuleSpecific($rule) }
    $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')))
    $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($systemIdentity, 'FullControl', 'Allow')))
    if ($PSVersionTable.PSVersion.Major -ge 6) {
        [System.IO.FileSystemAclExtensions]::SetAccessControl($fileInfo, $acl)
    } else {
        [System.IO.File]::SetAccessControl($envPath, $acl)
    }
} else {
    & chmod 600 $envPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict permissions on .env.' }
}
Write-Output 'Local .env ready; existing non-empty credentials preserved. No containers started.'
