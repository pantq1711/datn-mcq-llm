package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import org.springframework.http.HttpStatus;

/**
 * Lỗi của bước trích văn bản tài liệu, kèm mã máy đọc được (W2-3). Thông báo không chứa nội dung file hay tên file.
 */
final class MaterialErrors {

    private MaterialErrors() {}

    static ApiErrorException empty() {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File rỗng.");
    }

    static ApiErrorException tooLarge(long maxBytes) {
        return new ApiErrorException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                "File quá lớn: tối đa " + (maxBytes / (1024 * 1024)) + " MB mỗi file.");
    }

    static ApiErrorException unsupported(String message) {
        return new ApiErrorException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }

    static ApiErrorException invalid(String message) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_FILE", message);
    }

    static ApiErrorException noText(String message) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_EXTRACTABLE_TEXT", message);
    }

    static ApiErrorException tooLong(int maxChars) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "TEXT_TOO_LONG",
                "Tài liệu quá dài: tối đa " + maxChars + " ký tự văn bản. Hãy tách thành nhiều file hoặc chỉ giữ phần "
                        + "cần dùng.");
    }

    static ApiErrorException encrypted(String message) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "MATERIAL_ENCRYPTED", message);
    }

    static ApiErrorException tooManyPages(int maxPages) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_PAGES",
                "PDF quá dài: tối đa " + maxPages + " trang.");
    }

    static ApiErrorException failed(String message) {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "EXTRACTION_FAILED", message);
    }

    static ApiErrorException unavailable(String message) {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTOR_UNAVAILABLE", message);
    }

    static ApiErrorException busy() {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTOR_BUSY",
                "Hệ thống đang xử lý nhiều tài liệu, hãy thử lại sau ít phút.");
    }

    static ApiErrorException timeout() {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTION_TIMEOUT",
                "Đọc tài liệu quá lâu nên đã dừng. Hãy thử file nhỏ hơn hoặc ít trang hơn.");
    }
}
