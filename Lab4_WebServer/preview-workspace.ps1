$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
 $jars=@(Get-ChildItem target/*.jar | ForEach-Object FullName)+@(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
 $cp=$jars -join ';'
 New-Item -ItemType Directory -Path target/test-classes -Force | Out-Null
 & javac -encoding UTF-8 -cp $cp -d target/test-classes tests/IntegrationTest.java tests/TestDatabase.java tests/EmbeddedBrowserTest.java tests/WorkspacePreview.java
 if ($LASTEXITCODE -ne 0) { throw 'Workspace preview compilation failed' }
 & java '-Dfile.encoding=UTF-8' -cp "$cp;target/test-classes" WorkspacePreview
 if ($LASTEXITCODE -ne 0) { throw 'Workspace preview failed' }
} finally { Pop-Location }
