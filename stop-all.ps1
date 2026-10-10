#Requires -Version 5.1
<#
.SYNOPSIS
  停止「求职助手平台」本地进程：Agent（:8000/:8501）+ 后端（:8080）+ 前端（:5173）。

.DESCRIPTION
  默认只停 start-all.ps1 记录在 _runlogs\pids.txt 里的进程（不会误杀你在别处开的服务）。
  加 -Force 时会额外停掉「占用这四个端口」的进程——那是兜底手段，会先逐个打印出来是谁。

.PARAMETER Force
  连同占用 8000 / 8501 / 8080 / 5173 的进程一起停（请确认那些不是别的重要服务）。

.PARAMETER KeepAgent
  保留 Python Agent（只停后端与前端）。

.EXAMPLE
  .\stop-all.ps1
.EXAMPLE
  .\stop-all.ps1 -Force -KeepAgent
#>
[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$KeepAgent
)

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $root '_runlogs'
$pidFile = Join-Path $logDir 'pids.txt'
$portNames = @{ 8000 = 'Agent API'; 8501 = 'Agent 界面'; 8080 = '后端网关'; 5173 = '前端 Vite' }

function Stop-One([int]$procId, [string]$who) {
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $p) { Write-Host ('    [--] ' + $who + ' (PID ' + $procId + ') 已不在'); return }
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Host ('    [OK] 已停止 ' + $who + ' (PID ' + $procId + '，' + $p.ProcessName + ')') -ForegroundColor Green
    } catch {
        Write-Host ('    [XX] 停止失败 ' + $who + ' (PID ' + $procId + ')：' + $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host '==> 1/2 停止 start-all.ps1 记录的进程'
if (-not (Test-Path $pidFile)) {
    Write-Host '    没有 _runlogs\pids.txt（说明这些服务不是 start-all.ps1 起的）'
} else {
    foreach ($line in (Get-Content $pidFile -Encoding UTF8)) {
        if (-not $line.Trim()) { continue }
        $parts = $line -split '\s+', 2
        $procId = 0
        if (-not [int]::TryParse($parts[0], [ref]$procId)) { continue }
        $tag = if ($parts.Count -gt 1) { $parts[1] } else { 'unknown' }
        if ($KeepAgent -and $tag -like 'agent-*') { Write-Host ('    [--] 保留 ' + $tag + '（-KeepAgent）'); continue }
        Stop-One $procId $tag
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host '==> 2/2 端口占用检查'
$targets = @(8000, 8501, 8080, 5173)
if ($KeepAgent) { $targets = @(8080, 5173) }
foreach ($port in $targets) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $c) { Write-Host ('    [--] :' + $port + ' ' + $portNames[$port] + ' 已释放'); continue }
    $procId = ($c | Select-Object -First 1).OwningProcess
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    $who = $portNames[$port] + ' (' + $(if ($p) { $p.ProcessName } else { '未知' }) + ')'
    if ($Force) { Stop-One $procId $who }
    else { Write-Host ('    [!!] :' + $port + ' 仍被 ' + $who + ' 占用 —— 需要的话手动：Stop-Process -Id ' + $procId + ' -Force，或重跑 .\stop-all.ps1 -Force') -ForegroundColor Yellow }
}

Write-Host ''
Write-Host '完成。日志保留在 _runlogs\（要清空可删除该目录）。'
