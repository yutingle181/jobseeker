<#
 * 网关端到端监控基线压测脚本（对应文档 8.4 数据获取方法 / 9.2.D）。
 *
 * 用法：
 *   .\scripts\bench-gateway.ps1 -User <账号> -Pass <密码> [-N 20] [-BaseUrl http://localhost:8080]
 *
 * 行为：
 *   1) 先 POST /api/auth/login 拿 token（与 8.4 一致，带登录态测端到端）；
 *   2) 对 /agent/chat 发 N 次请求，用 curl -w "%{time_total}" 收集端到端耗时；
 *   3) 对 /agent/chat/stream 发 N 次请求，用 curl -w "%{time_starttransfer}" 收集首 token 延迟（TTFT）；
 *   4) 脚本内算 P50 / P95（秒→毫秒）并打印；
 *   5) 最后调一次 GET /agent/gateway-metrics 打印网关侧聚合快照，作为端到端 P95 基线。
 *
 * 说明：
 *   - 复用 8.4 的 curl 基准法，零额外依赖；
 *   - 缓存开关开启时，重复相同 query 会命中 X-Cache: HIT，此时网关 metrics 只累加 cacheHit、
 *     不记 Agent 时延——想测真实 Agent 时延请先清缓存或对 query 加随机后缀。
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User   = "test",
    [string]$Pass    = "test123",
    [int]   $N        = 20
)

function Pct($vals, $p) {
    if ($vals.Count -eq 0) { return 0 }
    $sorted = $vals | Sort-Object
    $idx = [math]::Ceiling($p * $sorted.Count) - 1
    if ($idx -lt 0)                  { $idx = 0 }
    if ($idx -ge $sorted.Count)      { $idx = $sorted.Count - 1 }
    return [math]::Round($sorted[$idx] * 1000, 1)   # 秒 -> 毫秒
}

function Avg($vals) {
    if ($vals.Count -eq 0) { return 0 }
    return [math]::Round(($vals | Measure-Object -Average).Average * 1000, 1)
}

Write-Host "== 登录拿 token ==" -ForegroundColor Cyan
$loginBody = "{`"username`":`"$User`",`"password`":`"$Pass`"}"
$token = (curl.exe -s -X POST "$BaseUrl/api/auth/login" -H "Content-Type: application/json" -d $loginBody | ConvertFrom-Json).token
if (-not $token) { Write-Error "登录失败，未拿到 token"; exit 1 }
$AuthHdr = "Authorization: Bearer $token"

Write-Host "== /agent/chat 端到端耗时 x$N ==" -ForegroundColor Cyan
$chatTimes = @()
for ($i = 0; $i -lt $N; $i++) {
    $t = [double](curl.exe -s -o $null -w "%{time_total}" -X POST "$BaseUrl/agent/chat" `
        -H $AuthHdr -H "Content-Type: application/json" -d '{"query":"1+1等于几"}')
    $chatTimes += $t
}
Write-Host ("   chat  avg={0}ms  P50={1}ms  P95={2}ms" -f (Avg $chatTimes),(Pct $chatTimes 0.5),(Pct $chatTimes 0.95))

Write-Host "== /agent/chat/stream 首 token 延迟(TTFT) x$N ==" -ForegroundColor Cyan
$ttftTimes = @()
for ($i = 0; $i -lt $N; $i++) {
    # time_starttransfer = 首字节（首事件）到达时间，即流式首 token 延迟
    $t = [double](curl.exe -s -N -o $null -w "%{time_starttransfer}" -X POST "$BaseUrl/agent/chat/stream" `
        -H $AuthHdr -H "Content-Type: application/json" -d '{"query":"你好"}')
    $ttftTimes += $t
}
Write-Host ("   stream TTFT  avg={0}ms  P50={1}ms  P95={2}ms" -f (Avg $ttftTimes),(Pct $ttftTimes 0.5),(Pct $ttftTimes 0.95))

Write-Host "== GET /agent/gateway-metrics (网关侧聚合基线) ==" -ForegroundColor Cyan
curl.exe -s "$BaseUrl/agent/gateway-metrics" -H $AuthHdr
Write-Host ""
