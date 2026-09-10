try {
    $r = Invoke-WebRequest -Uri 'http://localhost:5173/' -TimeoutSec 15 -ErrorAction Stop
    Write-Host ('根页面 HTTP ' + $r.StatusCode)
} catch {
    Write-Host ('根页面请求失败: ' + $_.Exception.Message)
}
# 清理 C: 上的测试残留
try { Remove-Item 'C:\vite-cache\test_b' -Recurse -Force -ErrorAction SilentlyContinue; Write-Host '已清理 C:\vite-cache\test_b' } catch { Write-Host '清理 test_b 跳过' }
# 尝试删除 D: 上本次排查留下的临时脚本(若被过滤拦截则无法删除)
foreach ($f in @('_defender_excl.ps1','_defender_reg.ps1','_kill_clean.ps1','_verify.ps1')) {
    $p = Join-Path 'd:\project\jobseeker\web' $f
    if (Test-Path $p) {
        try { Remove-Item $p -Force; Write-Host ('已删除 ' + $f) } catch { Write-Host ('删除 ' + $f + ' 失败(D: 过滤拦截): ' + $_.Exception.Message) }
    }
}
Write-Host '=== 收尾完成 ==='
