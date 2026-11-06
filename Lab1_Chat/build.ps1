$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path bin,lib -Force | Out-Null
    $driver = Join-Path $PSScriptRoot 'lib/mysql-connector-j-9.2.0.jar'
    if (-not (Test-Path -LiteralPath $driver)) {
        $cached = Join-Path $env:USERPROFILE '.m2/repository/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar'
        if (Test-Path -LiteralPath $cached) { Copy-Item -LiteralPath $cached -Destination $driver }
        else { Invoke-WebRequest 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar' -OutFile $driver }
    }
    $sources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 21 -cp 'lib/*' -d bin @sources
    if ($LASTEXITCODE -ne 0) { throw 'Lab 1 compilation failed' }
} finally { Pop-Location }
