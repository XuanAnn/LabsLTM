$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    $jars=@(Get-ChildItem target/*.jar | ForEach-Object FullName)+@(Get-ChildItem target/dependency/*.jar | ForEach-Object FullName)
    & java '-Dfile.encoding=UTF-8' -cp ($jars -join ';') client.GmailSettings
    if ($LASTEXITCODE -ne 0) { throw 'Không đăng nhập được tài khoản gửi thư.' }
} finally { Pop-Location }
