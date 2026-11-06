$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jars = @(Get-ChildItem target/*.jar | ForEach-Object FullName)
    $jars += @(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
    $cp = $jars -join ';'
    New-Item -ItemType Directory -Force target/test-classes | Out-Null
    & javac -encoding UTF-8 -cp $cp -d target/test-classes tests/IntegrationTest.java tests/TestDatabase.java tests/EmbeddedBrowserTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Browser test compilation failed' }
    & java '-Dfile.encoding=UTF-8' -cp "$cp;target/test-classes" EmbeddedBrowserTest
    if ($LASTEXITCODE -ne 0) { throw 'Embedded browser checks failed' }
} finally { Pop-Location }
