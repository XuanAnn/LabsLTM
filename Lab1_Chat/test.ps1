$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path target/test-classes -Force | Out-Null
    & javac -encoding UTF-8 -cp 'bin;lib/*' -d target/test-classes tests/IntegrationTest.java tests/ChatDatabaseFixture.java
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & java '-Dfile.encoding=UTF-8' -cp 'bin;lib/*;target/test-classes' IntegrationTest
    if ($LASTEXITCODE -ne 0) { throw 'Lab 1 tests failed' }
} finally { Pop-Location }
