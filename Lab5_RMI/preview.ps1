$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jars = @(Get-ChildItem -LiteralPath "$PSScriptRoot\target" -Filter '*.jar' | ForEach-Object FullName)
    $jars += @(Get-ChildItem -LiteralPath "$PSScriptRoot\target\dependency" -Filter '*.jar' | ForEach-Object FullName)
    $cp = $jars -join ';'
    New-Item -ItemType Directory -Path target/test-classes -Force | Out-Null
    & javac -encoding UTF-8 -cp $cp -d target/test-classes tests/UiSmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'UI check compilation failed' }
    & java '-Dfile.encoding=UTF-8' -cp "$cp;target/test-classes" UiSmoke "$PSScriptRoot\docs"
    if ($LASTEXITCODE -ne 0) { throw 'UI check failed' }
} finally { Pop-Location }
