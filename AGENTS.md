# Hướng dẫn cho AI agent

Trước khi làm bất kỳ việc gì trong repository này, hãy đọc và tuân thủ
[`docs/ai-development-rules.md`](docs/ai-development-rules.md) (hiện là v2).
Nếu tài liệu đó mâu thuẫn với file này, tài liệu đó thắng.

## Tóm tắt bắt buộc

1. Dùng quy trình chuẩn cho mỗi issue:
   `inspect → plan → [xác nhận] → test → implement → verify → diff review → [chờ xác nhận]`.
2. Chỉ sửa trong phạm vi đã được duyệt. Phát hiện ngoài phạm vi thì báo cáo, không tự sửa.
3. Không tự chạy khi chưa được cho phép rõ ràng trong lượt hiện tại:
   Maven/test, ứng dụng, migration, endpoint, lệnh database, Gemini/embedding hay dịch vụ tính phí.
4. Không đọc, in hay commit secret (`.env`, API key).
5. Không commit, push, checkout, reset hay sửa lịch sử Git khi chưa được yêu cầu.
6. Báo cáo test bằng đúng các nhãn trong mục 7 của tài liệu quy tắc; không coi báo cáo cũ là bằng chứng.
7. Các câu hỏi Q-x (mục 10 của tài liệu quy tắc) chưa được chốt: gặp thì dừng lại hỏi, không tự quyết.
8. Shell lỗi: tối đa 2 lần thử cho cùng mục tiêu, sau đó dừng và báo cáo.
Chạy test LUÔN bằng đúng một lệnh: powershell -ExecutionPolicy Bypass -File back_end/run-tests.ps1
- Chạy riêng: ghi tên test (phân tách bằng dấu phẩy, không khoảng trắng) vào back_end/.test-selection bằng công cụ sửa file của IDE, rồi chạy lệnh trên.
- Chạy toàn bộ: không tạo file đó; dòng đầu output phải là "=== CHAY TOAN BO TEST ===".
- Không dùng dạng lệnh test khác. Không dùng lệnh PowerShell ghi file (Set-Content, Out-File, WriteAllText...) và không tạo script .py để sửa code.
## Lưu ý cho người dùng

Không phải AI client nào cũng tự động đọc `AGENTS.md`. Khi giao việc, hãy tham chiếu rõ trong prompt,
ví dụ: `Tuân thủ docs/ai-development-rules.md v2. Lượt này chỉ làm bước inspect và plan.`,
hoặc cấu hình client để nạp tài liệu này.

## Quy ước commit

- Commit message bằng tiếng Anh, KHÔNG có dòng Co-Authored-By, Claude-Session hay chữ ký AI; PR description không có dòng "Generated with ...".
- Không dùng git commit --no-verify (repo dùng hook commit-msg để lọc các dòng trên).
