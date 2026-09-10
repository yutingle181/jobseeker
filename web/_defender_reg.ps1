$ErrorActionPreference = 'Stop'
# 检查 WinDefend 服务状态
try {
    $svc = Get-Service -Name 'WinDefend' -ErrorAction SilentlyContinue
    if ($svc) { Write-Host ('WinDefend 服务状态: ' + $svc.Status) } else { Write-Host 'WinDefend 服务不存在(可能是第三方杀软)' }
} catch {
    Write-Host ('查询 WinDefend 失败: ' + $_.Exception.Message)
}
# 通过注册表添加 Defender 路径排除项(等价于 Add-MpPreference -ExclusionPath)
try {
    $key = 'HKLM:\SOFTWARE\Microsoft\Windows Defender\Exclusions\Paths'
    if (-not (Test-Path $key)) { New-Item -Path $key -Force | Out-Null; Write-Host '已创建 Exclusions\Paths 注册表项' }
    $p = 'd:\project\jobseeker\web\node_modules\.vite'
    New-ItemProperty -Path $key -Name $p -Value 0 -PropertyType DWORD -Force | Out-Null
    Write-Host ('OK: 已写入 Defender 排除项 -> ' + $p)
} catch {
    Write-Host ('注册表排除项写入失败: ' + $_.Exception.Message)
}
