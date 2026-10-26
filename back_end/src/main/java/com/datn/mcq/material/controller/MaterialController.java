package com.datn.mcq.material.controller;

import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.material.dto.MaterialDtos.MaterialDto;
import com.datn.mcq.material.dto.MaterialDtos.MaterialPageDto;
import com.datn.mcq.material.service.MaterialService;
import com.datn.mcq.material.service.MaterialService.UploadResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Tài liệu bài học đính kèm (W2-3): chỉ giáo viên; tài liệu thuộc người tải lên (lấy từ token), của người khác trả 404.
 * Server chỉ lưu văn bản đã trích và sha256, không lưu file gốc.
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    /**
     * Tải lên một tài liệu (multipart, phần {@code file}): .docx, .pdf hoặc .txt UTF-8, tối đa 10 MB. Loại file nhận
     * dạng từ nội dung, bỏ qua đuôi file và Content-Type. 201 khi tạo mới; 200 và trả lại bản cũ khi chính giáo viên này
     * đã tải đúng file đó (cùng sha256). Mã lỗi: 400 EMPTY_FILE, MISSING_FILE; 409 MATERIAL_QUOTA_EXCEEDED; 413
     * FILE_TOO_LARGE; 415 UNSUPPORTED_FILE_TYPE; 422 INVALID_FILE, NO_EXTRACTABLE_TEXT, TEXT_TOO_LONG,
     * MATERIAL_ENCRYPTED, TOO_MANY_PAGES, EXTRACTION_FAILED; 429 TOO_MANY_UPLOADS; 503 EXTRACTOR_UNAVAILABLE,
     * EXTRACTOR_BUSY, EXTRACTION_TIMEOUT.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialDto> uploadMaterial(CurrentUser user, @RequestParam("file") MultipartFile file)
            throws IOException {
        UploadResult result = service.upload(user.id(), file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.material());
    }

    /** Tài liệu của tôi, mới nhất trước, không kèm văn bản. {@code size} từ 1 đến 50. */
    @GetMapping
    public MaterialPageDto listMaterials(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return service.list(user.id(), page, size);
    }

    /** Chi tiết một tài liệu kèm {@code textPreview} (tối đa 500 ký tự đầu của văn bản đã trích). */
    @GetMapping("/{id}")
    public MaterialDto getMaterial(CurrentUser user, @PathVariable Integer id) {
        return service.get(id, user.id());
    }

    /** Xóa tài liệu của tôi (204). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaterial(CurrentUser user, @PathVariable Integer id) {
        service.delete(id, user.id());
    }
}
