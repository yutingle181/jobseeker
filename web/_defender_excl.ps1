$ErrorActionPreference = 'Stop'
try {
    Add-MpPreference -ExclusionPath 'd:\project\jobseeker\web\node_modules\.vite' -ErrorAction Stop
    Write-Host 'OK: 已添加 .vite 排除项'
} catch {
    Write-Host ('ERR add .vite: ' + $_.Exception.Message)
    try {
        Add-MpPreference -ExclusionPath 'd:\project\jobseeker\web\node_modules' -ErrorAction Stop
        Write-Host 'OK: 已添加兜底 node_modules 排除项'
    } catch {
        Write-Host ('ERR add node_modules: ' + $_.Exception.Message)
    }
}
try {
    $ex = (Get-MpPreference).ExclusionPath
    Write-Host ('当前排除项: ' + ($ex -join '; '))
} catch {
    Write-Host ('无法读取排除项列表: ' + $_.Exception.Message)
}
