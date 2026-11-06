$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jars = @(Get-ChildItem target/*.jar | ForEach-Object FullName)
    $jars += @(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
    $cp = $jars -join ';'
    $source = 'src/common/SaveOptions.java'
    $copy = 'target/save-options-src/ltm/mail/support/SaveOptions.java'
    New-Item -ItemType Directory -Force (Split-Path $copy),target/save-options-classes | Out-Null
    $content = Get-Content -LiteralPath $source -Raw
    $content = $content.Replace('ENABLED.add(', '// ENABLED.add(')
    Set-Content -LiteralPath $copy -Value $content -Encoding utf8
    & javac -encoding UTF-8 -cp $cp -d target/save-options-classes $copy tests/IntegrationTest.java tests/TestDatabase.java tests/SaveOptionsTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Save-options compilation failed' }
    # The temporary policy is first on classpath; application source and jar stay unchanged.
    & java '-Dfile.encoding=UTF-8' -cp "target/save-options-classes;$cp" SaveOptionsTest
    if ($LASTEXITCODE -ne 0) { throw 'Save-options checks failed' }
} finally { Pop-Location }
