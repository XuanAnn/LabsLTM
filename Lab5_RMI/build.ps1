$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & mvn -q clean package dependency:copy-dependencies
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
} finally { Pop-Location }
