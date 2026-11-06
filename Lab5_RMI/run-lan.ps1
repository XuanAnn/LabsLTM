param(
    [Parameter(Mandatory=$true)][ValidateSet('A','B')][string]$Node,
    [ValidateSet('server','client')][string]$App = 'server',
    [string]$LocalIp,
    [string]$PeerIp
)
$ErrorActionPreference = 'Stop'
if ($App -eq 'client') {
    $registryPort = if ($Node -eq 'A') { '1099' } else { '2099' }
    & "$PSScriptRoot/run.ps1" client '127.0.0.1' $registryPort
} else {
    if ([string]::IsNullOrWhiteSpace($LocalIp) -or [string]::IsNullOrWhiteSpace($PeerIp)) {
        throw 'Nhap -LocalIp (IPv4 may nay) va -PeerIp (IPv4 may con lai).'
    }
    foreach ($address in @($LocalIp, $PeerIp)) {
        $parsedAddress = $null
        if (-not [System.Net.IPAddress]::TryParse($address, [ref]$parsedAddress) -or
            $parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
            [System.Net.IPAddress]::IsLoopback($parsedAddress)) {
            throw 'Dung IPv4 LAN thuc cho LocalIp va PeerIp, khong dung localhost/127.0.0.1.'
        }
    }
    & "$PSScriptRoot/run.ps1" server $Node $LocalIp $PeerIp
}
