/**
 * Module quản lý ảnh câu hỏi (thiết kế G0, PR #61, duyệt 11/11/2026).
 * Lưu trữ ảnh dạng binary, cấp URL có chữ ký HMAC-SHA256 phục vụ xem trước và hiển thị trên web.
 * Module lá chỉ phụ thuộc common, config, user; truy vấn kiểm quyền qua native SQL.
 */
package com.datn.mcq.questionimage;
