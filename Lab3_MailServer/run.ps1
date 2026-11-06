param([ValidateSet('server','client','server-cli')][string]$App='client', [Parameter(ValueFromRemainingArguments=$true)][string[]]$AppArgs)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jars = @(Get-ChildItem -LiteralPath "$PSScriptRoot\target" -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $jars += @(Get-ChildItem -LiteralPath "$PSScriptRoot\target\dependency" -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object FullName)
    if ($jars.Count -lt 2) { throw 'Run .\build.ps1 first' }
    $mainClass = if ($App -eq 'server') { 'server.ServerUI' } elseif ($App -eq 'server-cli') { 'server.MailServer' } else { 'client.ClientUI' }
    & java '-Dfile.encoding=UTF-8' -cp ($jars -join ';') $mainClass @AppArgs
    if ($LASTEXITCODE -ne 0) { throw "Java exited with $LASTEXITCODE" }
} finally { Pop-Location }
