param(
    [Parameter(Mandatory=$true)][string]$RuntimeHome,
    [Parameter(Mandatory=$true)][string]$WixHome,
    [string]$OutputDirectory,
    [switch]$Build
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$version=(Get-Content (Join-Path $projectRoot 'package.json') -Raw | ConvertFrom-Json).version
if(-not $OutputDirectory){$OutputDirectory=Join-Path $projectRoot ".local/assistant-release/$version"}
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
$runtime=(Resolve-Path -LiteralPath $RuntimeHome).Path
$wix=(Resolve-Path -LiteralPath $WixHome).Path
if((Get-Content (Join-Path $runtime 'release') -Raw) -notmatch 'IMPLEMENTOR="Eclipse Adoptium"'){throw 'Use the documented redistributable Eclipse Temurin JRE, not an Oracle desktop JDK.'}
foreach($tool in @('candle.exe','light.exe')){if(-not(Test-Path -LiteralPath (Join-Path $wix $tool))){throw "Missing WiX tool: $tool"}}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Output directory already exists. Choose a new directory; existing artifacts are never deleted.'}
if($Build){
    $maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath=if($maven){$maven.Source}else{'D:\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3\bin\mvn.cmd'}
    Push-Location (Join-Path $projectRoot 'backend')
    try{& $mavenPath -B verify;if($LASTEXITCODE -ne 0){throw 'Backend verification failed'}}finally{Pop-Location}
}
$jar=Join-Path $projectRoot "backend/target/ai-footprint-$version.jar"
if(-not(Test-Path -LiteralPath $jar)){throw 'Build the backend first or pass -Build.'}
$stage=New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'input')
Copy-Item -LiteralPath $jar -Destination $stage.FullName
Copy-Item -LiteralPath (Join-Path $projectRoot 'LICENSE') -Destination $stage.FullName
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/THIRD_PARTY.md') -Destination $stage.FullName
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/ASSISTANT.md') -Destination $stage.FullName
$imageParent=Join-Path $OutputDirectory 'image'
$jpackage=(Get-Command jpackage.exe -ErrorAction Stop).Source
& $jpackage --type app-image --name 'AI Footprint Assistant' --app-version $version --vendor 'AI Footprint' --description 'Local AI usage collector and paired browser bridge' --input $stage.FullName --main-jar "ai-footprint-$version.jar" --main-class org.springframework.boot.loader.launch.JarLauncher --runtime-image $runtime --java-options '-Xmx256m' --java-options '-Daif.desktop=true' --java-options '-Djava.awt.headless=false' --dest $imageParent
if($LASTEXITCODE -ne 0){throw 'App image packaging failed'}
$image=Join-Path $imageParent 'AI Footprint Assistant'
$originalPath=$env:PATH
try{
    $env:PATH="$wix;$originalPath"
    & $jpackage --type exe --app-image $image --name 'AI Footprint Assistant' --app-version $version --vendor 'AI Footprint' --win-per-user-install --win-menu --win-menu-group 'AI Footprint' --win-shortcut --win-dir-chooser --win-upgrade-uuid 'c88fc3a2-072f-4f7a-93f1-42257cb72ef9' --license-file (Join-Path $projectRoot 'LICENSE') --temp (Join-Path $OutputDirectory 'installer-temp') --dest $OutputDirectory
    if($LASTEXITCODE -ne 0){throw 'Windows installer packaging failed'}
}finally{$env:PATH=$originalPath}
Rename-Item -LiteralPath (Join-Path $OutputDirectory "AI Footprint Assistant-$version.exe") -NewName "AI-Footprint-Assistant-$version-windows-x64-setup.exe"
Compress-Archive -Path $image -DestinationPath (Join-Path $OutputDirectory "AI-Footprint-Assistant-$version-windows-x64-portable.zip")
$artifacts=Get-ChildItem -LiteralPath $OutputDirectory -File | Where-Object Extension -in '.exe','.zip'
$artifacts | ForEach-Object { "$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)" } | Set-Content -LiteralPath (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding ascii
$artifacts | Select-Object Name,Length,FullName
