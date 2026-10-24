# Kiểm tra MathType ĐỌC được đối tượng spike: mở bản sao docx, chọn toàn bộ, chạy "Toggle TeX" của MathType
# (đối tượng -> TeX). Đối tượng spike không lưu TeX gốc nên MathType phải phân tích MTEF để dịch ra TeX.
# Ghi văn bản từng đoạn ra file UTF-8. Không sửa file vào.
# Dùng: powershell -NoProfile -File mathtype_roundtrip.ps1 <in.docx> <out.txt>
param([Parameter(Mandatory = $true)][string]$InDocx, [Parameter(Mandatory = $true)][string]$OutTxt)

$ErrorActionPreference = 'Stop'
$src = (Resolve-Path $InDocx).Path
$copy = Join-Path ([IO.Path]::GetTempPath()) ("mt-roundtrip-" + [guid]::NewGuid() + ".docx")
Copy-Item -LiteralPath $src -Destination $copy
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
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
    $word.Quit([ref]0)
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    Remove-Item -LiteralPath $copy -ErrorAction SilentlyContinue
}
