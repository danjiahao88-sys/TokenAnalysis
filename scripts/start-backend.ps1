param([switch]$Build)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $projectRoot 'backend')
try {
    $jarPath = Join-Path (Get-Location) 'target/ai-footprint-0.4.0.jar'
    if ($Build -or -not (Test-Path -LiteralPath $jarPath)) {
        $mavenCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if (-not $mavenCmd) {
            $ideaMaven = 'D:\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3\bin\mvn.cmd'
            if (Test-Path -LiteralPath $ideaMaven) { $mavenPath = $ideaMaven }
            else { throw '请安装 Maven 3.9+ 并加入 PATH，然后重试。' }
        } else { $mavenPath = $mavenCmd.Source }
        & $mavenPath -B verify
        if ($LASTEXITCODE -ne 0) { throw '后端验证失败，请查看上方输出。' }
    }
    & java -Xmx256m -jar $jarPath
} finally { Pop-Location }
