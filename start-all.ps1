#Requires -Version 5.1
<#
.SYNOPSIS
  一键启动求职助手平台：Python Agent（:8000/:8501）+ Java 网关（:8080）+ Vue3 前端（:5173）。

.DESCRIPTION
  两种模式，自动选择：
    · 有 Docker  → docker compose up --build -d（原行为，服务在容器里）
    · 无 Docker  → 本地进程方式（本机就是这种）：Agent → 后端 → 前端，与 README「快速开始」等价

  相比直接照着手动敲命令，这个脚本多了几件"救过人"的事：
    1. 前置检查：JDK17 / Maven / Node / MySQL 缺哪个直接说清楚，而不是到第 3 步才报错；
    2. 幂等：已在监听的端口直接跳过，不会重复起进程（重复起会抢端口、日志互相覆盖）；
    3. 日志集中：全部落到 _runlogs\，失败时自动打印尾部，不用满屏翻找；
    4. 前端首次启动的 esbuild 崩溃（依赖预构建被安全软件打断）会自动清缓存重试一次；
    5. 启动完做健康检查，模型额度/网络不通时明确告知（服务是好的、但回答会走降级）。

.PARAMETER Mode
  auto（默认）/ docker / local。

.PARAMETER SkipAgent
  跳过 Python Agent（例如你已经在别的窗口跑着它）。

.PARAMETER SkipWeb
  跳过前端（只起 Agent + 后端）。

.PARAMETER TimeoutSeconds
  单个服务的等待上限，默认 240 秒。

.EXAMPLE
  .\start-all.ps1                 # 有 Docker 走 Docker，没有就走本地
.EXAMPLE
  .\start-all.ps1 -Mode local -SkipWeb
.EXAMPLE
  .\stop-all.ps1                  # 停止（对应脚本）
#>
[CmdletBinding()]
param(
    [ValidateSet('auto', 'docker', 'local')][string]$Mode = 'auto',
    [switch]$SkipAgent,
    [switch]$SkipWeb,
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = 'Continue'
# 控制台按 UTF-8 解码：否则 mvn / npm 等外部进程的中文日志在 PS 5.1 下会显示成乱码
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$agentDir = Join-Path (Split-Path -Parent $root) 'Agent实习项目'
$webDir = Join-Path $root 'web'
$serverDir = Join-Path $root 'server'
$logDir = Join-Path $root '_runlogs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$pidFile = Join-Path $logDir 'pids.txt'
$JDK = 'C:\Program Files\Java\jdk-17'
$started = New-Object System.Collections.ArrayList

function Write-Step([string]$text) { Write-Host ''; Write-Host ('==> ' + $text) -ForegroundColor Cyan }
function Write-Ok([string]$text) { Write-Host ('    [OK] ' + $text) -ForegroundColor Green }
function Write-Warn2([string]$text) { Write-Host ('    [!!] ' + $text) -ForegroundColor Yellow }
function Write-Err2([string]$text) { Write-Host ('    [XX] ' + $text) -ForegroundColor Red }

function Test-Port([int]$port) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    return [bool]$c
}
function Get-PortPid([int]$port) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($c) { return ($c | Select-Object -First 1).OwningProcess }
    return 0
}
function Wait-Port([int]$port, [int]$timeoutSec, $proc) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        if (Test-Port $port) { return $true }
        if ($proc -and $proc.HasExited) { return $false }
        Start-Sleep -Seconds 2
    }
    return (Test-Port $port)
}
function Show-Tail([string]$file, [int]$lines = 8) {
    if (-not (Test-Path $file)) { return }
    Write-Host ('    --- ' + (Split-Path $file -Leaf) + ' ---')
    Get-Content $file -Tail $lines -ErrorAction SilentlyContinue | ForEach-Object { Write-Host ('      ' + $_) }
}
function New-Logged([string]$exe, [string[]]$arguments, [string]$workDir, [string]$tag) {
    $o = Join-Path $logDir ($tag + '.out.log')
    $e = Join-Path $logDir ($tag + '.err.log')
    Remove-Item $o, $e -Force -ErrorAction SilentlyContinue
    $p = Start-Process -FilePath $exe -ArgumentList $arguments -WorkingDirectory $workDir `
        -WindowStyle Hidden -RedirectStandardOutput $o -RedirectStandardError $e -PassThru
    [void]$started.Add(@{ Tag = $tag; Pid = $p.Id; Id = $tag + '(' + $p.Id + ')' })
    return $p
}
function Resolve-AgentPython {
    foreach ($v in @('.venv', '.venv-ci')) {
        $p = Join-Path $agentDir "$v\Scripts\python.exe"
        if (Test-Path $p) { return $p }
    }
    return $null
}

Write-Host '============================================'
Write-Host '  求职助手平台 · 一键启动'
Write-Host ('  项目根目录：' + $root)
Write-Host '============================================'

# ---------------------------------------------------------------- 0. 模式选择
$dockerCli = Get-Command docker -ErrorAction SilentlyContinue
$dockerUsable = $false
if ($dockerCli) {
    & docker info *> $null
    $dockerUsable = ($LASTEXITCODE -eq 0)
}
$useDocker = $false
if ($Mode -eq 'docker') { $useDocker = $true }
elseif ($Mode -eq 'auto' -and $dockerUsable) { $useDocker = $true }

Write-Step '0/4 启动方式'
if ($Mode -eq 'docker' -and -not $dockerUsable) { Write-Warn2 '指定了 docker 模式，但 docker 不可用（CLI 缺失或守护进程未运行）'; Write-Warn2 '若本机没有容器运行时，请改用 -Mode local' }
if ($useDocker) {
    Write-Ok 'Docker 可用 → 走 docker compose（Agent 仍建议本机跑，见 README）'
} else {
    if ($Mode -eq 'auto') { Write-Ok '未检测到可用的 Docker → 自动改用本地进程方式（与 README 快速开始等价）' }
    else { Write-Ok '本地进程方式' }
}

if ($useDocker) {
    Write-Step '1/2 启动容器（mysql + server + web）'
    Set-Location $root
    & docker compose up --build -d
    Write-Step '2/2 容器状态'
    & docker compose ps
    Write-Host ''
    Write-Host '启动完成（Docker）：'
    Write-Host '  前端        http://localhost'
    Write-Host '  后端 API    http://localhost:8080'
    Write-Host '  Agent       http://localhost:8000/docs   （如需本机运行：.\start-all.ps1 -Mode local -SkipWeb）'
    exit 0
}

# ---------------------------------------------------------------- 1. 前置检查
Write-Step '1/4 前置检查'
$missing = New-Object System.Collections.ArrayList

if (-not (Test-Path (Join-Path $JDK 'bin\java.exe'))) { [void]$missing.Add('JDK 17（期望 ' + $JDK + '）') }
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) { [void]$missing.Add('Maven（mvn 不在 PATH）') }
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { [void]$missing.Add('Node.js（node 不在 PATH）') }
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npm) { [void]$missing.Add('npm') }

$mysqlSvc = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'mysql|MariaDB' -and $_.Status -eq 'Running' } | Select-Object -First 1
if ($mysqlSvc) { Write-Ok ('MySQL 服务运行中：' + $mysqlSvc.Name) }
elseif (Test-Port 3306) { Write-Ok 'MySQL 端口 3306 已在监听' }
else { Write-Warn2 '未发现运行中的 MySQL（3306 空闲）—— 后端会连接失败，请先启动 MySQL 服务' }

if (Test-Path $mvn) { Write-Ok ('Maven：' + $mvn.Source) }
if (Test-Path $node) { Write-Ok ('Node：' + (& node -v)) }
if (-not $missing -and (Test-Path $JDK)) { Write-Ok ('JDK 17：' + $JDK) }

if ($missing.Count -gt 0) {
    Write-Err2 '缺少必需的前置条件，已中止（补齐后重跑）：'
    foreach ($m in $missing) { Write-Host ('      - ' + $m) -ForegroundColor Red }
    exit 1
}

# ---------------------------------------------------------------- 2. Agent
Write-Step '2/4 Python Agent（:8000 API / :8501 界面）'
if ($SkipAgent) { Write-Warn2 '按参数跳过' }
elseif ((Test-Port 8000) -and (Test-Port 8501)) {
    Write-Ok ('已在运行，跳过（PID ' + (Get-PortPid 8000) + ' / ' + (Get-PortPid 8501) + '）')
} else {
    if (-not (Test-Path $agentDir)) {
        Write-Err2 ('找不到 Agent 目录：' + $agentDir)
    } else {
        $py = Resolve-AgentPython
        if (-not $py) {
            Write-Warn2 'Agent 下没有 .venv / .venv-ci，请先在 Agent 目录跑 .\run_api.ps1 让它自动建环境'
        } else {
            Write-Ok ('Python 环境：' + $py)
            if (Test-Port 8000) { Write-Warn2 ':8000 已在监听，跳过 API' }
            else {
                $api = New-Logged $py @('-m', 'uvicorn', 'src.api.main:app', '--host', '127.0.0.1', '--port', '8000') $agentDir 'agent-api'
                Write-Ok ('启动 Agent API（PID ' + $api.Id + '），等待 :8000 ...')
                if (Wait-Port 8000 60 $api) { Write-Ok 'Agent API 就绪' }
                else { Write-Err2 'Agent API 未在 60 秒内监听'; Show-Tail (Join-Path $logDir 'agent-api.err.log') }
            }
            if (-not $SkipWeb -and -not (Test-Port 8501)) {
                $web = New-Logged $py @('-m', 'streamlit', 'run', (Join-Path $agentDir 'app.py'), '--server.headless', 'true', '--browser.gatherUsageStats', 'false', '--server.port', '8501') $agentDir 'agent-ui'
                Write-Ok ('启动 Agent 界面（PID ' + $web.Id + '），等待 :8501 ...')
                if (Wait-Port 8501 60 $web) { Write-Ok 'Agent 界面就绪' }
                else { Write-Warn2 'Agent 界面未就绪（不影响 API 与平台使用）'; Show-Tail (Join-Path $logDir 'agent-ui.err.log') }
            }
        }
    }
}

# ---------------------------------------------------------------- 3. 后端
Write-Step '3/4 后端网关（:8080）'
if (Test-Port 8080) {
    Write-Ok ('已在运行，跳过（PID ' + (Get-PortPid 8080) + '）')
} else {
    $settings = Join-Path $serverDir '.mvn\local-settings.xml'
    $tpl = Join-Path $root '.mvn\local-settings.xml.example'
    if (-not (Test-Path $settings)) {
        if (Test-Path $tpl) { Copy-Item $tpl $settings -Force; Write-Warn2 '首次运行：已从模板复制 server\.mvn\local-settings.xml（请按本机改 localRepository 路径）' }
        else { Write-Warn2 '缺少 server\.mvn\local-settings.xml 且无模板，Maven 可能用默认仓库（本机默认仓库可能无写权限）' }
    }
    $env:JAVA_HOME = $JDK
    $be = New-Logged $mvn.Source @('-s', '.mvn/local-settings.xml', 'spring-boot:run') $serverDir 'backend'
    Write-Ok ('启动后端（PID ' + $be.Id + '），首次编译可能 1-3 分钟 ...')
    if (Wait-Port 8080 $TimeoutSeconds $be) { Write-Ok '后端就绪' }
    else {
        Write-Err2 '后端未在 ' + $TimeoutSeconds + ' 秒内监听 :8080'
        Show-Tail (Join-Path $logDir 'backend.out.log') 12
        Show-Tail (Join-Path $logDir 'backend.err.log')
    }
}

# ---------------------------------------------------------------- 4. 前端
Write-Step '4/4 前端（:5173，Vite）'
if ($SkipWeb) { Write-Warn2 '按参数跳过' }
elseif (Test-Port 5173) {
    Write-Ok ('已在运行，跳过（PID ' + (Get-PortPid 5173) + '）')
} else {
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Warn2 '缺少 web\node_modules，先安装依赖（npmmirror 源）...'
        Push-Location $webDir
        & npm install --registry=https://registry.npmmirror.com
        Pop-Location
    }
    $fe = New-Logged $npm.Source @('run', 'dev') $webDir 'frontend'
    Write-Ok ('启动 Vite（PID ' + $fe.Id + '），等待 :5173 ...')
    $ok = Wait-Port 5173 60 $fe
    if (-not $ok) {
        # 首次运行常见故障：依赖预构建（esbuild）被安全软件打断 → 清缓存重试一次
        $errLog = Join-Path $logDir 'frontend.err.log'
        $hit = $false
        if (Test-Path $errLog) {
            $hit = [bool](Select-String -Path $errLog -Pattern 'esbuild|service was stopped|EACCES' -ErrorAction SilentlyContinue)
        }
        if ($hit) {
            Write-Warn2 '检测到 esbuild 早期退出（多为安全软件拦截依赖预构建）→ 清 .vite 缓存后重试一次'
            Remove-Item (Join-Path $webDir 'node_modules\.vite') -Recurse -Force -ErrorAction SilentlyContinue
            $fe = New-Logged $npm.Source @('run', 'dev') $webDir 'frontend2'
            $ok = Wait-Port 5173 60 $fe
        }
    }
    if ($ok) { Write-Ok '前端就绪' }
    else {
        Write-Err2 '前端未在时限内监听 :5173'
        Show-Tail (Join-Path $logDir 'frontend.err.log') 10
        Show-Tail (Join-Path $logDir 'frontend2.err.log') 6
    }
}

# ---------------------------------------------------------------- 5. 汇总
Write-Step '启动结果'
$rows = @(
    @{ Port = 8000; Name = 'Agent API'; Url = 'http://127.0.0.1:8000/docs' },
    @{ Port = 8501; Name = 'Agent 界面'; Url = 'http://127.0.0.1:8501' },
    @{ Port = 8080; Name = '后端网关'; Url = 'http://127.0.0.1:8080' },
    @{ Port = 5173; Name = '前端应用'; Url = 'http://127.0.0.1:5173' }
)
foreach ($r in $rows) {
    $alive = Test-Port $r.Port
    $tag = if ($alive) { '[OK]' } else { '[--]' }
    $line = '    ' + $tag + ' :' + $r.Port + '  ' + $r.Name.PadRight(10) + ' ' + $r.Url
    if ($alive) { Write-Host $line -ForegroundColor Green } else { Write-Host $line -ForegroundColor DarkGray }
}

# 健康检查：服务通不通 / 模型可不可用（额度、网络）
if (Test-Port 8000) {
    try {
        # 注意：PS 5.1 的 Invoke-RestMethod 在响应头未带 charset 时按 Latin-1 解码 → 中文报错信息乱码，
        # 所以这里手工按 UTF-8 解码（llm_detail 里常常就是中文/中文混英文的失败原因）。
        $resp = Invoke-WebRequest 'http://127.0.0.1:8000/health' -UseBasicParsing -TimeoutSec 20
        $h = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
        if ($h.status -eq 'ok') {
            Write-Ok ('Agent /health = ok（llm=' + $h.llm + '，embedding=' + $h.embedding + '，checkpoint=' + $h.runtime.checkpoint + '）')
        } else {
            Write-Warn2 ('Agent /health = ' + $h.status + ' —— 服务已起，但模型/向量不可用，AI 回答会走降级路径')
            if ($h.llm_detail) { Write-Host ('      原因：' + ([string]$h.llm_detail).Split([char]10)[0]) -ForegroundColor DarkYellow }
        }
    } catch { Write-Warn2 ('/health 读取失败：' + $_.Exception.Message) }
}

# 记录 PID 供 stop-all.ps1 使用
foreach ($s in $started) { Write-Host ('    [PID] ' + $s.Id) }
if ($started.Count -gt 0) {
    $lines = $started | ForEach-Object { [string]$_.Pid + '  ' + $_.Tag }
    Set-Content -Path $pidFile -Value $lines -Encoding UTF8
    Write-Host ('    本次启动的 PID 已记入：' + $pidFile)
}

Write-Host ''
Write-Host ('日志目录：' + $logDir)
Write-Host '停止：.\stop-all.ps1        （只停本次启动的；-Force 还会停占用这四个端口的进程）'
Write-Host '手动停止单个：Stop-Process -Id 8000 对应的 PID（见上表 / pids.txt）'
