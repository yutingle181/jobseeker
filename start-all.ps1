# 一键启动：先拉起 Python Agent，再启动本软件（server + web）
# 说明：Agent 是独立服务，必须比本软件先起来，网关与归档都依赖它。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$agentDir = Join-Path (Split-Path -Parent $root) 'Agent实习项目'

Write-Host '==> 1/3 启动 Python Agent (:8000)' -ForegroundColor Cyan
Start-Process powershell -ArgumentList '-NoExit', '-Command', "cd '$agentDir'; .\run_api.ps1"
Start-Sleep -Seconds 6

Write-Host '==> 2/3 检查 Agent 健康状态' -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health' -TimeoutSec 10
    Write-Host ("    Agent 状态: {0}" -f $health.status) -ForegroundColor Green
} catch {
    Write-Host '    警告：Agent 尚未就绪，网关与归档会失败。请确认 .env 已配置后重试。' -ForegroundColor Yellow
}

Write-Host '==> 3/3 启动本软件（MySQL + server:8080 + web:80）' -ForegroundColor Cyan
Set-Location $root
docker compose up --build -d

Write-Host ''
Write-Host '启动完成：' -ForegroundColor Green
Write-Host '  前端        http://localhost'
Write-Host '  后端 API    http://localhost:8080'
Write-Host '  Agent       http://localhost:8000/docs'
Write-Host '  Agent 界面  http://localhost:8501'
