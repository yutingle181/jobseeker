$d = 'C:\vite-cache\jobseeker-web'
if (Test-Path $d) {
    Write-Host ('cacheDir 已创建: ' + $d)
    Get-ChildItem $d | ForEach-Object { Write-Host ('  ' + $_.Name) }
    $deps = Join-Path $d 'deps'
    if (Test-Path $deps) { Write-Host 'SUCCESS: deps/ 目录已生成(重命名成功)' } else { Write-Host 'WARN: 仍无 deps/ 目录' }
} else {
    Write-Host 'WARN: cacheDir 未创建'
}
Start-Sleep -Seconds 2
foreach ($dep in @('vue.js','pinia.js','tdesign-vue-next.js')) {
    try {
        $r = Invoke-WebRequest -Uri ('http://localhost:5173/node_modules/.vite/deps/' + $dep) -TimeoutSec 20 -ErrorAction Stop
        Write-Host ($dep + ' -> HTTP ' + $r.StatusCode)
    } catch {
        Write-Host ($dep + ' -> 失败: ' + $_.Exception.Message)
    }
}
