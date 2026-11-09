param([string]$ProxyUrl=$env:AIF_BUILD_PROXY)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$destination=Join-Path $projectRoot '.tools/packaging'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
function Download-Verified([string]$Name,[string]$Uri,[string]$Sha256) {
    $file=Join-Path $destination $Name
    if(-not(Test-Path -LiteralPath $file)) {
        $request=@{Uri=$Uri;OutFile=$file;TimeoutSec=180}
        if($ProxyUrl){$request.Proxy=$ProxyUrl}
        Invoke-WebRequest @request
    }
    if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Sha256){throw "Checksum mismatch: $Name"}
    return $file
}
$runtime=Download-Verified 'OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip' 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip' '2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628'
$wix=Download-Verified 'wix314-binaries.zip' 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
# Fixed official archive hashes; extraction never executes a downloaded setup script.
if(-not(Test-Path -LiteralPath (Join-Path $destination 'jdk-21.0.13+9-jre/release'))){Expand-Archive -LiteralPath $runtime -DestinationPath $destination}
if(-not(Test-Path -LiteralPath (Join-Path $destination 'wix/candle.exe'))){Expand-Archive -LiteralPath $wix -DestinationPath (Join-Path $destination 'wix')}
Write-Output 'Packaging dependencies verified.'
