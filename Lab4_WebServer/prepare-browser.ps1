$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jars = @(Get-ChildItem target/*.jar | ForEach-Object FullName)
    $jars += @(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
    & java '-Dfile.encoding=UTF-8' -cp ($jars -join ';') client.BrowserRuntime
    if ($LASTEXITCODE -ne 0) { throw 'Browser installation failed' }
} finally { Pop-Location }
