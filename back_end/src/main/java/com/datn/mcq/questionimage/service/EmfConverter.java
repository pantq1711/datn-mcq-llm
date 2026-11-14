package com.datn.mcq.questionimage.service;

import java.util.Map;

/**
 * Giao diện chuyển đổi ảnh vector EMF sang bitmap PNG.
 */
public interface EmfConverter {

    /**
     * Kiểm tra công cụ chuyển đổi EMF có sẵn sàng trên hệ thống hay không (ví dụ LibreOffice soffice).
     */
    boolean isAvailable();

    /**
     * Chuyển đổi cả lô ảnh EMF trong một lượt.
     *
     * @param emfFiles Map chứa [khóa ảnh -> dữ liệu byte EMF thô]
     * @return Map chứa [khóa ảnh -> kết quả chuyển đổi]
     */
    Map<String, EmfConvertResult> convertBatch(Map<String, byte[]> emfFiles);
}
