package com.datn.mcq.bankimport.model;

/**
 * Nguồn của một bộ câu hỏi, lấy từ tên file ("KHỐI 8 - CHƯƠNG 3 - BÀI 1 - TÊN BÀI - GV …"). Tên file gốc và tên giáo viên
 * KHÔNG được lưu ở đây hay ở bất kỳ output nào; {@code fileSha256} (16 ký tự đầu) để nhận ra file.
 */
public record Source(String docId, Integer grade, Integer chapter, Integer lesson, String lessonTitle, String fileSha256) {}
