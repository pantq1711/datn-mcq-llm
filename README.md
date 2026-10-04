# datn-mcq-llm

Đồ án tốt nghiệp (PTIT): hệ thống sinh câu hỏi trắc nghiệm bằng LLM, có bước kiểm tra chất lượng và giáo viên duyệt. Hệ thống cũng được dùng để chạy một thực nghiệm so sánh các cách sinh câu hỏi.

Nhóm: Phan Hồng An, Vũ Hồng Quân. Giảng viên hướng dẫn: ThS. Nguyễn Mạnh Sơn.

## Hệ thống làm gì

Từ một câu mẫu, LLM sinh ra câu trắc nghiệm 4 phương án, 1 đáp án đúng. Câu hỏi đi qua các bước: kiểm tra định dạng, kiểm tra trùng lặp, chấm độ khó, rồi giáo viên duyệt. Câu được duyệt vào ngân hàng câu hỏi và dùng để tạo đề.

Phần thực nghiệm so sánh 3 cách sinh: không ví dụ, kèm 3 ví dụ, và kèm ví dụ cộng đầu ra có cấu trúc. Giáo viên chấm mù một mẫu câu hỏi để đo chất lượng từng cách, hiệu quả của bước lọc tự động, và mức đồng ý giữa LLM với giáo viên về độ khó. Chi tiết nằm trong đề cương.

## Trạng thái

Đang thiết kế, chưa có code. Hiện mới có schema CSDL và danh sách enum dùng chung.

## Thư mục

- `db/schema_final.sql`: schema đầy đủ, gồm cả các ràng buộc CHECK.
- `db/schema_final_erd.sql`: bản bỏ CHECK và DEFAULT, chỉ để import vào Visual Paradigm vẽ ERD.
- `docs/contract/enums.md`: các giá trị enum mà backend và frontend phải dùng giống nhau.

Backend, frontend, OpenAPI, sơ đồ và prompt sẽ thêm vào sau.

## Cách làm việc

- Không push thẳng vào `main`. Làm trên nhánh, mở pull request, người còn lại xem qua rồi mới merge.
- Đổi schema thì viết migration mới, không sửa file cũ. Đổi enum thì sửa `enums.md` trong cùng commit.
- Không commit khóa API. Copy `.env.example` thành `.env` rồi điền giá trị thật, `.env` đã nằm trong `.gitignore`.
