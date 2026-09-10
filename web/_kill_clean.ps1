$ErrorActionPreference = 'Continue'
function Kill-Tree($pidv) {
    if (-not $pidv) { return }
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$pidv" -ErrorAction SilentlyContinue
    foreach ($c in $children) { Kill-Tree $c.ProcessId }
    try { Stop-Process -Id $pidv -Force -ErrorAction SilentlyContinue; Write-Host ("已停止 PID " + $pidv) } catch { Write-Host ("停止 PID $pidv 失败: " + $_.Exception.Message) }
}
# 1) 占用 5173 的进程
$p5173 = (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue).OwningProcess
if ($p5173) { Write-Host ("5173 占用 PID: " + $p5173); Kill-Tree $p5173 } else { Write-Host '5173 当前无占用' }
# 2) 所有与 jobseeker\web 相关的 node / esbuild 进程
$nodes = Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and ($_.CommandLine -like '*jobseeker\web*' -or $_.CommandLine -like '*esbuild*') }
foreach ($n in $nodes) { Write-Host ("杀 node PID " + $n.ProcessId + " : " + $n.CommandLine); Kill-Tree $n.ProcessId }
# 3) npm run dev 的 cmd 壳
$cmds = Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and $_.CommandLine -like '*npm run dev*' -and $_.CommandLine -like '*jobseeker*' }
foreach ($c in $cmds) { Write-Host ("杀 cmd PID " + $c.ProcessId); Kill-Tree $c.ProcessId }
Start-Sleep -Seconds 3
$p5173b = (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue).OwningProcess
if ($p5173b) { Write-Host ("清理后 5173: 仍占用 PID " + $p5173b) } else { Write-Host '清理后 5173: 空闲' }
# 4) 清理损坏的 .vite
$target = 'd:\project\jobseeker\web\node_modules\.vite'
if (Test-Path $target) {
    & cmd /c ("rmdir /s /q ""{0}""" -f $target)
    Start-Sleep -Seconds 1
    if (Test-Path $target) { Write-Host '删除 .vite 失败(仍被锁/被拦截)' } else { Write-Host '已删除 .vite' }
} else { Write-Host '.vite 不存在' }
# 5) 清理 timestamp 临时文件
Get-ChildItem 'd:\project\jobseeker\web\vite.config.ts.timestamp-*' -ErrorAction SilentlyContinue | ForEach-Object { try { [System.IO.File]::Delete($_.FullName); Write-Host ('已删除 ' + $_.Name) } catch { Write-Host ('删除 ' + $_.Name + ' 失败: ' + $_.Exception.Message) } }
Write-Host '=== 清场完成 ==='
