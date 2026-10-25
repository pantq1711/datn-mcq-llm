# Tiến trình canh của office_guard.ps1, do New-GuardedWord bật (không chạy tay).
#  - Quá TimeoutSec mà script chính chưa xong: đóng Word của script và MathType mới khởi động, để lời gọi COM bị treo
#    (ví dụ vì hộp thoại lỗi của MathType) thoát ra và script chính chạy tiếp phần finally.
#  - Script chính kết thúc mà không ghi Marker (bị dừng giữa chừng): tự đóng các tiến trình đó và khôi phục
#    Cut and Copy Preferences từ PrefsFile.
param(
    [Parameter(Mandatory = $true)][int]$ParentPid,
    [int]$WordPid = 0,
    [string]$PreMathType = '',
    [Parameter(Mandatory = $true)][long]$StartTicks,
    [int]$TimeoutSec = 600,
    [Parameter(Mandatory = $true)][string]$Marker,
    [Parameter(Mandatory = $true)][string]$PrefsFile
)

. (Join-Path $PSScriptRoot 'office_guard.ps1')

$guard = [pscustomobject]@{
    StartTime   = [datetime]::new($StartTicks)
    PreMathType = @($PreMathType -split ',' | Where-Object { $_ -ne '' } | ForEach-Object { [int]$_ })
}

function Close-Owned {
    if ($WordPid -gt 0) { Stop-Process -Id $WordPid -Force -ErrorAction SilentlyContinue }
    Get-OgNewMathType $guard | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
}
if (Test-Path -LiteralPath $Marker) { exit 0 }

Close-Owned
# Hết giờ: script chính được gỡ treo và tự khôi phục trong finally; chờ nó xong.
$grace = (Get-Date).AddSeconds(30)
while ((Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) -and -not (Test-Path -LiteralPath $Marker) -and (Get-Date) -lt $grace) {
    Start-Sleep -Milliseconds 500
}
if (-not (Test-Path -LiteralPath $Marker)) {
    Close-Owned
    if (Test-Path -LiteralPath $PrefsFile) {
        $snapshot = Get-Content -LiteralPath $PrefsFile -Raw -Encoding UTF8 | ConvertFrom-Json
        Restore-OgPrefs @($snapshot) | Out-Null
        Remove-Item -LiteralPath $PrefsFile -ErrorAction SilentlyContinue
    }
}
