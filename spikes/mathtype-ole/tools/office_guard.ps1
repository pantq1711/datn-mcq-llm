# Dùng chung cho các script tự động Word/MathType. Dot-source: . (Join-Path $PSScriptRoot 'office_guard.ps1')
#
# Bảo đảm khi script kết thúc, kể cả khi lỗi hoặc bị treo vì hộp thoại của MathType (ví dụ "MathType Translation
# Error" là hộp thoại modal, chặn lời gọi COM $word.Run):
#   1. Đóng Word do script mở (xác định đúng tiến trình qua cửa sổ OpusApp có caption riêng) và MathType/MathTypeLib
#      khởi động trong lúc script chạy. Không đụng Word/MathType đã chạy từ trước khi script bắt đầu.
#   2. Khôi phục Cut and Copy Preferences của MathType (HKCU\Software\Design Science\DSMT7\General: CutAndCopy,
#      OutputTranslator, MTEFInTeX, TransNameInTeX) nếu bị đổi trong lúc chạy.
# Một tiến trình canh (office_guard_watchdog.ps1) chạy song song: quá thời gian thì đóng Word/MathType để lời gọi COM bị
# treo thoát ra; nếu script bị dừng giữa chừng (không chạy tới finally) thì tự dọn và khôi phục.
#
# Dùng:
#   $guard = Start-OfficeGuard -TimeoutSec 600
#   $word = $null
#   try { $word = New-GuardedWord $guard; ... } finally { Stop-OfficeGuard $guard $word }

$script:OgMathTypeNames = @('MathType', 'MathTypeLib')
$script:OgPrefKey = 'HKCU:\Software\Design Science\DSMT7\General'
$script:OgPrefNames = @('CutAndCopy', 'OutputTranslator', 'MTEFInTeX', 'TransNameInTeX')

if (-not ('OfficeGuard.Win32' -as [type])) {
    Add-Type -Namespace OfficeGuard -Name Win32 -MemberDefinition @'
[DllImport("user32.dll", CharSet = CharSet.Unicode)]
public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);
[DllImport("user32.dll")]
public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
'@
}

# Ảnh chụp các giá trị Cut and Copy Preferences: mảng {Name, Exists, Value, Kind}.
function Get-OgPrefs {
    $out = @()
    $key = Get-Item -LiteralPath $script:OgPrefKey -ErrorAction SilentlyContinue
    foreach ($n in $script:OgPrefNames) {
        if ($key -and ($key.GetValueNames() -contains $n)) {
            $out += [pscustomobject]@{ Name = $n; Exists = $true; Value = $key.GetValue($n); Kind = $key.GetValueKind($n).ToString() }
        } else {
            $out += [pscustomobject]@{ Name = $n; Exists = $false; Value = $null; Kind = $null }
        }
    }
    return $out
}

# Đưa các giá trị về như ảnh chụp; trả về tên các giá trị đã phải khôi phục.
function Restore-OgPrefs($snapshot) {
    $restored = @()
    if (-not (Test-Path -LiteralPath $script:OgPrefKey)) {
        return $restored
    }
    $now = Get-OgPrefs
    foreach ($s in $snapshot) {
        $cur = $now | Where-Object { $_.Name -eq $s.Name }
        $same = ($cur.Exists -eq $s.Exists) -and ("$($cur.Value)" -eq "$($s.Value)")
        if ($same) { continue }
        if ($s.Exists) {
            Set-ItemProperty -LiteralPath $script:OgPrefKey -Name $s.Name -Value $s.Value -Type $s.Kind
        } else {
            Remove-ItemProperty -LiteralPath $script:OgPrefKey -Name $s.Name -ErrorAction SilentlyContinue
        }
        $restored += "$($s.Name): '$($cur.Value)' -> '$($s.Value)'"
    }
    return $restored
}

function Get-OgNewMathType($guard) {
    @(Get-Process -Name $script:OgMathTypeNames -ErrorAction SilentlyContinue | Where-Object {
        ($guard.PreMathType -notcontains $_.Id) -and ($_.StartTime -ge $guard.StartTime)
    })
}

function Start-OfficeGuard {
    param([int]$TimeoutSec = 600)
    $id = [guid]::NewGuid().ToString('N')
    $tmp = [IO.Path]::GetTempPath()
    $guard = [pscustomobject]@{
        Id          = $id
        StartTime   = (Get-Date).AddSeconds(-1)
        PreMathType = @(Get-Process -Name $script:OgMathTypeNames -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
        PreWord     = @(Get-Process -Name 'WINWORD' -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
        Prefs       = Get-OgPrefs
        PrefsFile   = Join-Path $tmp "office-guard-$id.prefs.json"
        Marker      = Join-Path $tmp "office-guard-$id.done"
        WordPid     = 0
        Watchdog    = $null
        TimeoutSec  = $TimeoutSec
    }
    ConvertTo-Json -InputObject @($guard.Prefs) | Set-Content -LiteralPath $guard.PrefsFile -Encoding UTF8
    if ($guard.PreMathType.Count -gt 0) {
        "Guard: MathType da chay tu truoc (pid $($guard.PreMathType -join ',')), se khong dong cac tien trinh nay."
    }
    return $guard
}

# Tạo Word ẩn, ghi lại đúng PID của nó rồi mới bật tiến trình canh.
function New-GuardedWord($guard) {
    $word = New-Object -ComObject Word.Application
    $caption = "office-guard-$($guard.Id)"
    $word.Caption = $caption
    $procId = [uint32]0
    $hwnd = [OfficeGuard.Win32]::FindWindow('OpusApp', $caption)
    if ($hwnd -ne [IntPtr]::Zero) {
        [void][OfficeGuard.Win32]::GetWindowThreadProcessId($hwnd, [ref]$procId)
    }
    if ($procId -eq 0) {
        # Dự phòng: Word duy nhất mới xuất hiện sau khi script bắt đầu.
        $new = @(Get-Process -Name 'WINWORD' -ErrorAction SilentlyContinue | Where-Object {
            ($guard.PreWord -notcontains $_.Id) -and ($_.StartTime -ge $guard.StartTime)
        })
        if ($new.Count -eq 1) { $procId = [uint32]$new[0].Id }
    }
    $guard.WordPid = [int]$procId
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $watchdog = Join-Path $PSScriptRoot 'office_guard_watchdog.ps1'
    $guard.Watchdog = Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -PassThru -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$watchdog`"",
        '-ParentPid', $PID, '-WordPid', $guard.WordPid,
        '-PreMathType', "`"$($guard.PreMathType -join ',')`"",
        '-StartTicks', $guard.StartTime.Ticks, '-TimeoutSec', $guard.TimeoutSec,
        '-Marker', "`"$($guard.Marker)`"", '-PrefsFile', "`"$($guard.PrefsFile)`"")
    return $word
}

function Stop-OfficeGuard($guard, $word) {
    if ($word) {
        try { foreach ($d in @($word.Documents)) { try { $d.Close([ref]0) } catch { } } } catch { }
        try { $word.Quit([ref]0) } catch { }
        try { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($word) } catch { }
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
    [GC]::Collect()

    $killed = @()
    if ($guard.WordPid -gt 0) {
        $deadline = (Get-Date).AddSeconds(15)
        while ((Get-Process -Id $guard.WordPid -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 300
        }
        if (Get-Process -Id $guard.WordPid -ErrorAction SilentlyContinue) {
            Stop-Process -Id $guard.WordPid -Force -ErrorAction SilentlyContinue
            $killed += "WINWORD $($guard.WordPid)"
        }
    }
    # MathType thường tự thoát sau khi Word đóng; chờ thêm rồi mới buộc đóng.
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-OgNewMathType $guard).Count -gt 0 -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 300
    }
    foreach ($p in (Get-OgNewMathType $guard)) {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        $killed += "$($p.ProcessName) $($p.Id)"
    }

    # Khôi phục sau khi MathType đã thoát (MathType ghi preferences khi thoát).
    $restored = Restore-OgPrefs $guard.Prefs

    New-Item -ItemType File -Path $guard.Marker -Force | Out-Null
    if ($guard.Watchdog -and -not $guard.Watchdog.HasExited) {
        Stop-Process -Id $guard.Watchdog.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $guard.PrefsFile, $guard.Marker -ErrorAction SilentlyContinue

    if ($killed.Count -gt 0) { "Guard: buoc dong $($killed -join ', ')" } else { "Guard: Word/MathType cua script da dong" }
    if ($restored.Count -gt 0) { "Guard: khoi phuc Cut and Copy Preferences: $($restored -join '; ')" }
    $left = @()
    if ($guard.WordPid -gt 0 -and (Get-Process -Id $guard.WordPid -ErrorAction SilentlyContinue)) { $left += "WINWORD $($guard.WordPid)" }
    $left += (Get-OgNewMathType $guard | ForEach-Object { "$($_.ProcessName) $($_.Id)" })
    if ($left.Count -gt 0) { "Guard: CANH BAO con tien trinh: $($left -join ', ')" }
}
