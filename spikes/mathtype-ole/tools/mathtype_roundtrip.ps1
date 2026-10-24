# Kiểm tra MathType ĐỌC được đối tượng spike: mở bản sao docx, chọn toàn bộ, chạy "Toggle TeX" của MathType
# (đối tượng -> TeX). Đối tượng spike không lưu TeX gốc nên MathType phải phân tích MTEF để dịch ra TeX.
# Ghi văn bản từng đoạn ra file UTF-8. Không sửa file vào.
# Luôn đóng Word/MathType do script mở và khôi phục Cut and Copy Preferences (office_guard.ps1).
# -Translator (tùy chọn, vd. Texvc.tdl): đặt OutputTranslator của MathType trong lúc chạy, khôi phục khi xong.
# Dùng: powershell -NoProfile -File mathtype_roundtrip.ps1 <in.docx> <out.txt> [-Translator Texvc.tdl] [-TimeoutSec 600]
param([Parameter(Mandatory = $true)][string]$InDocx, [Parameter(Mandatory = $true)][string]$OutTxt,
      [string]$Translator, [int]$TimeoutSec = 600)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'office_guard.ps1')
$src = (Resolve-Path $InDocx).Path
$copy = Join-Path ([IO.Path]::GetTempPath()) ("mt-roundtrip-" + [guid]::NewGuid() + ".docx")
Copy-Item -LiteralPath $src -Destination $copy
$guard = Start-OfficeGuard -TimeoutSec $TimeoutSec
$word = $null
try {
    if ($Translator) {
        Set-ItemProperty -LiteralPath 'HKCU:\Software\Design Science\DSMT7\General' -Name 'OutputTranslator' -Value $Translator
        "OutputTranslator (tam thoi) = $Translator"
    }
    $word = New-GuardedWord $guard
    $doc = $word.Documents.Open($copy, $false, $false, $false)
    $before = $doc.InlineShapes.Count
    $doc.Content.Select()
    $word.Run('MTCommand_TeXToggle') | Out-Null
    $after = $doc.InlineShapes.Count
    $lines = @("InlineShapes truoc=$before sau=$after")
    $i = 0
    foreach ($p in $doc.Paragraphs) {
        $t = $p.Range.Text.Trim()
        if ($t -ne '') { $i++; $lines += "$i`t$t" }
    }
    [IO.File]::WriteAllLines($OutTxt, $lines, (New-Object Text.UTF8Encoding($false)))
    "InlineShapes before=$before after=$after; wrote $OutTxt"
    $doc.Close([ref]0)
} finally {
    Stop-OfficeGuard $guard $word
    Remove-Item -LiteralPath $copy -ErrorAction SilentlyContinue
}
