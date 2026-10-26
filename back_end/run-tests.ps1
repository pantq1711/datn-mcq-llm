# Chay test backend bang MOT lenh co dinh (de IDE chi can duyet mot lan).
#   Lenh:  powershell -ExecutionPolicy Bypass -File back_end/run-tests.ps1
# Chay rieng vai test: ghi ten vao back_end/.test-selection (vd: PassageSelectorTest,GroundingCheckerTest)
# Khong co file do thi chay TOAN BO test. File duoc xoa ngay sau khi doc.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$selFile = Join-Path $PSScriptRoot '.test-selection'
$sel = ''
if (Test-Path $selFile) {
    $sel = (Get-Content $selFile -Raw).Trim()
    Remove-Item $selFile
}
if ($sel -and $sel -notmatch '^[A-Za-z0-9_,#*.]+$') {
    Write-Error "Noi dung .test-selection khong hop le: $sel"
    exit 2
}
if ($sel) {
    Write-Host "=== CHAY RIENG: -Dtest=$sel ==="
    & .\mvnw.cmd test "-Dtest=$sel" "-Dsurefire.failIfNoSpecifiedTests=false"
} else {
    Write-Host "=== CHAY TOAN BO TEST ==="
    & .\mvnw.cmd test
}
exit $LASTEXITCODE
