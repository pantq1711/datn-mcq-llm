package com.datn.mcq.bankimport.controller;

import com.datn.mcq.bankimport.dto.BankImportDtos.BatchDto;
import com.datn.mcq.bankimport.dto.BankImportDtos.ConfirmRequest;
import com.datn.mcq.bankimport.dto.BankImportDtos.ConfirmResult;
import com.datn.mcq.bankimport.service.BankImportService;
import com.datn.mcq.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Nhập ngân hàng câu từ Word (V26): chỉ giáo viên; lô thuộc người tải (lấy từ token), lô của người khác trả 404
 * IMPORT_NOT_FOUND. Câu đã xác nhận vào ngân hàng RIÊNG TƯ của chính người tải. Không gọi LLM, không lưu file gốc.
 */
@RestController
@RequestMapping("/api/bank-imports")
public class BankImportController {

    private final BankImportService service;

    public BankImportController(BankImportService service) {
        this.service = service;
    }

    /**
     * Tải bộ câu hỏi .docx (multipart, phần {@code file}, tối đa 10 MB, tối đa 200 câu) → 201 bản xem trước (chưa lưu câu
     * nào). Khối, chương, bài, tên bài lấy từ tên file theo mẫu "KHỐI 8 - CHƯƠNG 1 - BÀI 2 - Tên bài"; {@code gradeLevel}
     * (6-9) và {@code topic} (tùy chọn) ghi đè. Câu có ảnh, công thức chưa chuyển được hoặc bảng dữ liệu bị bỏ và đếm theo
     * lý do. Mã lỗi: 400 EMPTY_FILE, MISSING_FILE, IMPORT_TOPIC_INVALID; 409 IMPORT_PENDING_LIMIT (đã có 3 lô chờ); 413
     * FILE_TOO_LARGE; 415 UNSUPPORTED_FILE_TYPE; 422 INVALID_FILE, IMPORT_LESSON_UNKNOWN, IMPORT_GRADE_INVALID,
     * IMPORT_NO_QUESTIONS, IMPORT_TOO_MANY_QUESTIONS; 429 TOO_MANY_UPLOADS; 503 EXTRACTOR_BUSY.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BatchDto uploadBankImport(CurrentUser user, @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "topic", required = false) String topic,
                                     @RequestParam(value = "gradeLevel", required = false) Integer gradeLevel)
            throws IOException {
        return service.upload(user.id(), file.getOriginalFilename(), file.getBytes(), topic, gradeLevel);
    }

    /** Xem lại một lô. Lô chờ xác nhận có danh sách câu kèm cờ; lô đã xác nhận không còn nội dung. 410 IMPORT_EXPIRED. */
    @GetMapping("/{id}")
    public BatchDto getBankImport(CurrentUser user, @PathVariable Integer id) {
        return service.get(id, user.id());
    }

    /**
     * Đưa các câu {@code keys} vào ngân hàng riêng tư của tôi (một transaction). Mã lỗi: 400 IMPORT_KEYS_INVALID (rỗng,
     * lặp, không có trong lô); 409 IMPORT_NOT_PREVIEW (đã xác nhận); 410 IMPORT_EXPIRED; 422 IMPORT_ITEM_BLOCKED (câu còn cờ
     * chặn, sửa trong file Word rồi tải lại).
     */
    @PostMapping("/{id}/confirm")
    public ConfirmResult confirmBankImport(CurrentUser user, @PathVariable Integer id,
                                           @RequestBody ConfirmRequest request) {
        return service.confirm(id, user.id(), request == null ? null : request.keys());
    }

    /** Hủy lô chưa xác nhận (204); lô đã xác nhận thì 409 IMPORT_NOT_PREVIEW. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBankImport(CurrentUser user, @PathVariable Integer id) {
        service.delete(id, user.id());
    }
}
