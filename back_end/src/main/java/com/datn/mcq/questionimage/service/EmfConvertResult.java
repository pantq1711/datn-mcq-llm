package com.datn.mcq.questionimage.service;

/**
 * Kết quả chuyển đổi một file EMF sang PNG.
 */
public record EmfConvertResult(
        boolean success,
        byte[] pngBytes,
        String errorCode,
        String errorMessage
) {
    public static EmfConvertResult ok(byte[] pngBytes) {
        return new EmfConvertResult(true, pngBytes, null, null);
    }

    public static EmfConvertResult failure(String errorCode, String errorMessage) {
        return new EmfConvertResult(false, null, errorCode, errorMessage);
    }
}
