param([string]$Config='config/database.properties')
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
 $jars=@(Get-ChildItem target/*.jar | ForEach-Object FullName)+@(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
 & java '-Dfile.encoding=UTF-8' -cp ($jars -join ';') common.DatabaseTool $Config
 if ($LASTEXITCODE -ne 0) { throw 'MySQL connection failed; see docs/MYSQL_WORKBENCH.md' }
} finally { Pop-Location }
