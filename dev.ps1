<#
.SYNOPSIS
    campus-market 本地开发环境：后台启动 / 停止后端与前端。

.DESCRIPTION
    默认起后端（8080，dev profile）+ 学生端 Web（5174）。
    -App admin / mini / all 追加另外两个前端（5173、5175）。
    进程 PID 写 .run\，控制台输出写 logs\dev\，后端业务日志仍在 campus-market\backend\logs\。

.EXAMPLE
    .\dev.ps1
    起后端 + 学生端 Web（最常用）

.EXAMPLE
    .\dev.ps1 -App all
    起后端 + 学生端 / 管理后台 / 小程序 H5

.EXAMPLE
    .\dev.ps1 -Build
    改过 Java 代码：先打包再起（会先停掉本脚本启动的进程）

.EXAMPLE
    .\dev.ps1 stop
    停掉本脚本启动的所有进程（不动你手动起的实例）

.EXAMPLE
    .\dev.ps1 status
    看端口与 PID 状态

.EXAMPLE
    .\dev.ps1 -App mini
    起后端 + 小程序 H5（-App 可逗号多选，如 -App web,admin）

.EXAMPLE
    .\dev.ps1 logs
    跟踪后端业务日志（Ctrl+C 退出）

.EXAMPLE
    .\dev.ps1 help
    列出全部动作、前端参数与开关
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs', 'help')]
    [string]$Action = 'start',

    # 支持多选：-App web,admin；all = 三端；none = 只起后端
    [ValidateSet('web', 'admin', 'mini', 'all', 'none')]
    [string[]]$App = @('web'),

    [switch]$Build
)

$ErrorActionPreference = 'Stop'

# ---------- 路径 ----------
$Root    = $PSScriptRoot
$Backend = Join-Path $Root 'campus-market\backend'
$Front   = Join-Path $Root 'campus-market-web'
$RunDir  = Join-Path $Root '.run'
$LogDir  = Join-Path $Root 'logs\dev'
$Mvn     = 'D:\program files\apache-maven-3.9.11\bin\mvn.cmd'
$Jar     = Join-Path $Backend 'campus-admin-api\target\campus-admin-api-1.0.0-SNAPSHOT.jar'

$BackendPort = 8080

# 前端三端：端口与包名。mini 是独立工程（不进 pnpm workspace），脚本名也不同。
$FrontApps = @{
    web   = @{ Label = '学生端 Web';   Port = 5174; Filter = '@campus/web';   Dir = $Front }
    admin = @{ Label = '管理后台';     Port = 5173; Filter = '@campus/admin'; Dir = $Front }
    mini  = @{ Label = '小程序 H5';    Port = 5175; Filter = $null;           Dir = (Join-Path $Front 'apps\mini') }
}

# 展开 all、过滤 none、去重（保序）：-App all,web 与 -App all 等价
$ALL_FRONTS = @('web', 'admin', 'mini')
$wantedFronts = @()
foreach ($pick in $App) {
    if ($pick -eq 'none') { continue }
    if ($pick -eq 'all') { $wantedFronts += $ALL_FRONTS; continue }
    $wantedFronts += $pick
}
if ($wantedFronts.Count -gt 0) {
    $wantedFronts = @($wantedFronts | Select-Object -Unique)
}

# ---------- 工具函数 ----------
function Test-PortOpen {
    param([int]$Port)
    # 用「本机是否有监听」而不是「能否连上 127.0.0.1」：
    # vite 的 host 是 localhost，Node 解析成 ::1，只绑 IPv6，连 127.0.0.1 必然失败。
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        if ($null -ne $conn) { return $true }
    } catch {
        # 老系统没有该 cmdlet 时退回双栈探测
        foreach ($addr in @('127.0.0.1', '::1')) {
            $client = New-Object System.Net.Sockets.TcpClient
            try {
                $async = $client.BeginConnect($addr, $Port, $null, $null)
                if ($async.AsyncWaitHandle.WaitOne(300) -and $client.Connected) { return $true }
            } catch {
            } finally {
                $client.Close()
            }
        }
    }
    return $false
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSec, [string]$Label)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Start-Detached {
    param(
        [string]$CommandLine,
        [string]$WorkDir,
        [string]$OutFile,
        [string]$ErrFile
    )
    # 为什么不用 Start-Process -RedirectStandardOutput：
    # PS 5.1 在重定向时会重建环境变量字典，本机同时存在 HTTPS_PROXY / https_proxy
    # （名字大小写不同、字典又按不区分大小写索引）直接抛 "Item has already been added"。
    # 改用 cmd 自己重定向，UseShellExecute=true 不碰那张字典。
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = '/s /c "' + $CommandLine + ' > "' + $OutFile + '" 2> "' + $ErrFile + '"' + '"'
    $psi.UseShellExecute = $true
    $psi.WindowStyle = 'Hidden'
    $psi.WorkingDirectory = $WorkDir
    return [System.Diagnostics.Process]::Start($psi)
}

function Get-SavedPid {
    param([string]$Name)
    $file = Join-Path $RunDir ($Name + '.pid')
    if (-not (Test-Path $file)) { return 0 }
    $raw = (Get-Content $file -Raw).Trim()
    if ($raw -match '^\d+$') { return [int]$raw }
    return 0
}

function Test-Alive {
    param([int]$ProcessId)
    if ($ProcessId -le 0) { return $false }
    $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    return ($null -ne $p)
}

function Format-PidCell {
    param([int]$ProcessId)
    if ($ProcessId -le 0) { return '—' }
    if (Test-Alive -ProcessId $ProcessId) { return ([string]$ProcessId) }
    return ($ProcessId.ToString() + '(已退出)')
}

function Stop-All {
    $names = @('backend') + $FrontApps.Keys
    $stopped = 0
    foreach ($name in $names) {
        $processId = Get-SavedPid -Name $name
        if ($processId -le 0) { continue }
        if (Test-Alive -ProcessId $processId) {
            # /T 连子孙一起杀：pnpm -> node -> esbuild 是一条链，只杀 pnpm 会留孤儿占端口
            & taskkill /PID $processId /T /F 2>$null | Out-Null
            Write-Host ("  [stop] {0} (PID {1})" -f $name, $processId) -ForegroundColor DarkGray
            $stopped++
        }
        Remove-Item (Join-Path $RunDir ($name + '.pid')) -Force -ErrorAction SilentlyContinue
    }
    if ($stopped -eq 0) { Write-Host '  没有本脚本启动的进程需要停止' -ForegroundColor DarkGray }
}

function Invoke-BuildBackend {
    Write-Host '  打包后端（mvn package，约 1-2 分钟）…' -ForegroundColor Cyan
    Push-Location $Backend
    try {
        & $Mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败（exit $LASTEXITCODE）" }
    } finally {
        Pop-Location
    }
    Write-Host '  打包完成' -ForegroundColor Green
}

function Start-Backend {
    if (Test-PortOpen -Port $BackendPort) {
        Write-Host ("  [skip] 后端 {0} 已被占用（不是本脚本起的，stop 不会动它）" -f $BackendPort) -ForegroundColor DarkYellow
        return
    }
    if (-not (Test-Path $Jar)) {
        throw ("jar 不存在：{0}`n  先执行：.\dev.ps1 -Build" -f $Jar)
    }

    $out = Join-Path $LogDir 'backend.out.log'
    $err = Join-Path $LogDir 'backend.err.log'
    $cmdLine = 'java -jar "' + $Jar + '" --spring.profiles.active=dev --server.port=' + $BackendPort
    $proc = Start-Detached -CommandLine $cmdLine -WorkDir $Backend -OutFile $out -ErrFile $err

    Set-Content -Path (Join-Path $RunDir 'backend.pid') -Value $proc.Id -Encoding ascii
    Write-Host ("  [start] 后端 PID {0}，等 8080 就绪…" -f $proc.Id) -ForegroundColor Gray

    if (Wait-Port -Port $BackendPort -TimeoutSec 90 -Label 'backend') {
        Write-Host ("  [ok] 后端 8080 就绪") -ForegroundColor Green
    } else {
        Write-Host '  [warn] 90 秒内未见 8080，检查 logs\dev\backend.err.log' -ForegroundColor Red
    }
}

function Start-FrontApp {
    param([string]$Key)

    $meta = $FrontApps[$Key]
    if (Test-PortOpen -Port $meta.Port) {
        Write-Host ("  [skip] {0} {1} 已被占用" -f $meta.Label, $meta.Port) -ForegroundColor DarkYellow
        return
    }

    # mini 走自己的 dev:h5，其余走 workspace filter（避免重复跑 pnpm tokens 打架）
    if ($meta.Filter) {
        $cmdLine = 'pnpm --filter ' + $meta.Filter + ' dev'
    } else {
        $cmdLine = 'pnpm dev:h5'
    }

    $out = Join-Path $LogDir ($Key + '.out.log')
    $err = Join-Path $LogDir ($Key + '.err.log')
    # 经 cmd.exe 起，taskkill /T 才能顺着 cmd -> node 整棵树收掉
    $proc = Start-Detached -CommandLine $cmdLine -WorkDir $meta.Dir -OutFile $out -ErrFile $err

    Set-Content -Path (Join-Path $RunDir ($Key + '.pid')) -Value $proc.Id -Encoding ascii
    Write-Host ("  [start] {0} PID {1}，等 {2} 就绪…" -f $meta.Label, $proc.Id, $meta.Port) -ForegroundColor Gray

    if (Wait-Port -Port $meta.Port -TimeoutSec 60 -Label $Key) {
        Write-Host ("  [ok] {0} http://localhost:{1}" -f $meta.Label, $meta.Port) -ForegroundColor Green
    } else {
        Write-Host ("  [warn] 60 秒内未见 {0}，检查 {1}" -f $meta.Port, $err) -ForegroundColor Red
    }
}

function Invoke-TokensBuild {
    # 多端同时起时才需要：单端直接跑 dev，它自己会处理；多端并发跑 pnpm tokens 会互相覆盖
    if ($wantedFronts.Count -le 1) { return }
    Write-Host '  构建 design tokens（多端共用，只跑一次）…' -ForegroundColor Gray
    Push-Location $Front
    try { & cmd.exe /c 'pnpm tokens' | Out-Null } finally { Pop-Location }
}

function Show-Endpoints {
    Write-Host ''
    Write-Host '--------------------------------------------' -ForegroundColor DarkGray
    Write-Host '访问地址' -ForegroundColor Cyan
    Write-Host ("  后端 API     http://localhost:{0}" -f $BackendPort)
    foreach ($key in $wantedFronts) {
        Write-Host ("  {0}   http://localhost:{1}" -f $FrontApps[$key].Label, $FrontApps[$key].Port)
    }
    Write-Host '  查看状态     .\dev.ps1 status' -ForegroundColor DarkGray
    Write-Host '  停止服务     .\dev.ps1 stop' -ForegroundColor DarkGray
    Write-Host '  全部命令     .\dev.ps1 help' -ForegroundColor DarkGray
    Write-Host '  后端业务日志 campus-market\backend\logs\campus-market.log' -ForegroundColor DarkGray
}

# ---------- 分支 ----------
New-Item -ItemType Directory -Path $RunDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

switch ($Action) {

    'start' {
        Write-Host ''
        Write-Host '== campus-market 开发环境 ==' -ForegroundColor Cyan

        if ($Build) {
            # 打包会锁 jar，先停后端，否则 package 报文件占用
            Stop-All
            Invoke-BuildBackend
        }

        Start-Backend
        Invoke-TokensBuild
        foreach ($key in $wantedFronts) { Start-FrontApp -Key $key }
        Show-Endpoints
    }

    'stop' {
        Write-Host ''
        Write-Host '== 停止 ==' -ForegroundColor Cyan
        Stop-All
        foreach ($port in (@($BackendPort) + ($FrontApps.Values | ForEach-Object { $_.Port }))) {
            if (Test-PortOpen -Port $port) {
                Write-Host ("  [note] {0} 仍有监听（不是本脚本起的）" -f $port) -ForegroundColor DarkYellow
            }
        }
        Write-Host ''
    }

    'restart' {
        Write-Host ''
        Write-Host '== 重启 ==' -ForegroundColor Cyan
        Stop-All
        Start-Sleep -Seconds 1
        if ($Build) { Invoke-BuildBackend }
        Start-Backend
        Invoke-TokensBuild
        foreach ($key in $wantedFronts) { Start-FrontApp -Key $key }
        Show-Endpoints
    }

    'status' {
        Write-Host ''
        Write-Host '== 服务状态 ==' -ForegroundColor Cyan

        $rows = @()
        $rows += ,@('后端 8080', $BackendPort, 'backend')
        foreach ($key in @('web', 'admin', 'mini')) {
            $rows += ,@(($FrontApps[$key].Label + ' ' + $FrontApps[$key].Port), $FrontApps[$key].Port, $key)
        }

        foreach ($row in $rows) {
            $state = 'DOWN  '
            if (Test-PortOpen -Port $row[1]) { $state = 'LISTEN' }
            $pidCell = Format-PidCell -ProcessId (Get-SavedPid -Name $row[2])
            Write-Host ("  {0}  {1}   PID {2}" -f $row[0].PadRight(14), $state, $pidCell)
        }
        Write-Host ''
    }

    'help' {
        Write-Host ''
        Write-Host '== dev.ps1 · campus-market 本地开发环境 ==' -ForegroundColor Cyan
        Write-Host '  用法：.\dev.ps1 [动作] -App [前端] [-Build]'
        Write-Host '        动作默认 start；前端默认 web，可逗号多选（-App web,admin）'
        Write-Host ''
        Write-Host '动作（默认 start）' -ForegroundColor Cyan
        Write-Host '  start      起后端 + 前端；进程在隐藏窗口跑，关掉当前终端不受影响'
        Write-Host '  stop       停掉本脚本起的进程；你手动起的实例不会被杀'
        Write-Host '  restart    先 stop 再 start，参数与 start 相同'
        Write-Host '  status     列出四个端口的监听状态与 PID'
        Write-Host '  logs       跟踪后端业务日志（Ctrl+C 退出）'
        Write-Host '  help       显示本帮助'
        Write-Host ''
        Write-Host '前端 -App（逗号多选，顺序无关，重复自动去重）' -ForegroundColor Cyan
        Write-Host '  web        学生端 Web     http://localhost:5174（默认）'
        Write-Host '  admin      管理后台       http://localhost:5173'
        Write-Host '  mini       小程序 H5      http://localhost:5175'
        Write-Host '  all        等价于 web,admin,mini'
        Write-Host '  none       只起后端，不启任何前端'
        Write-Host ''
        Write-Host '开关' -ForegroundColor Cyan
        Write-Host '  -Build     先 mvn package 再起；改过 Java 代码时用'
        Write-Host ''
        Write-Host '示例 · 单端' -ForegroundColor Cyan
        Write-Host '  .\dev.ps1 -App web              后端 + 学生端（等同 .\dev.ps1）'
        Write-Host '  .\dev.ps1 -App admin            后端 + 管理后台'
        Write-Host '  .\dev.ps1 -App mini             后端 + 小程序 H5'
        Write-Host '  .\dev.ps1 -App none             只起后端（纯接口调试）'
        Write-Host ''
        Write-Host '示例 · 组合' -ForegroundColor Cyan
        Write-Host '  .\dev.ps1 -App web,admin        后端 + 学生端 + 管理后台'
        Write-Host '  .\dev.ps1 -App web,mini         后端 + 学生端 + 小程序'
        Write-Host '  .\dev.ps1 -App admin,mini       后端 + 管理后台 + 小程序'
        Write-Host '  .\dev.ps1 -App all              后端 + 三端（= web,admin,mini）'
        Write-Host ''
        Write-Host '示例 · 常用动作' -ForegroundColor Cyan
        Write-Host '  .\dev.ps1 status                查看四个端口与 PID'
        Write-Host '  .\dev.ps1 stop                  停止本脚本起的全部进程'
        Write-Host '  .\dev.ps1 restart -App all      重启三端'
        Write-Host '  .\dev.ps1 restart -Build        重新打包后重启后端 + 学生端'
        Write-Host '  .\dev.ps1 -Build -App mini      改过 Java 代码：打包后起后端 + 小程序'
        Write-Host '  .\dev.ps1 logs                  跟踪后端业务日志'
        Write-Host '  .\dev.ps1 help                  本帮助'
        Write-Host ''
        Write-Host '日志：logs\dev\（控制台输出）、campus-market\backend\logs\campus-market.log（业务日志）' -ForegroundColor DarkGray
        Write-Host ''
    }

    'logs' {
        $springLog = Join-Path $Backend 'logs\campus-market.log'
        if (-not (Test-Path $springLog)) { throw ("日志不存在：{0}（后端还没跑过？）" -f $springLog) }
        Write-Host ("跟踪 {0}  —— Ctrl+C 退出" -f $springLog) -ForegroundColor DarkGray
        Get-Content $springLog -Tail 40 -Wait -Encoding UTF8
    }
}
