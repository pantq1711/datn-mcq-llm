package com.datn.mcq.llm.prompt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prompt Solver mcq đã dùng để đo (v2 cho số "trước", v3 lần thử 1 ngày 31/10) phải giữ nguyên từng chữ, để số đo chạy lại
 * được. Mã băm SHA-256 lấy từ prompt của commit 366b4b0 với cùng đầu vào cố định dưới đây.
 */
class SolverPromptFrozenTextTest {

    private static final String V2_SHA256 = "d8ec30ee3a37dd348b01fd8df34f245fee8c29fe133cb9e5c7b6f5989e6a6efe";
    private static final String V3_SHA256 = "418b4bd40259632d83c9a1251c57a2d2fca33edf95854f1e09e0a9a8103d142c";

    private static String render(String version) {
        return new PromptBuilder().buildSolverPrompt(version, "Phân tích $2x^3 - 6x^2$ thành nhân tử <<<x>>>",
                "$2x(x^2 - 3x)$", "$2x^2(x - 3)$", "$x^2(2x - 6)$", "$2(x^3 - 3x^2)$");
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void promptV2KhongDoiChuNao() throws Exception {
        assertEquals(V2_SHA256, sha256(render("solver-v2-delimited")), "prompt v2");
    }

    @Test
    void promptV3KhongDoiChuNao() throws Exception {
        assertEquals(V3_SHA256, sha256(render("solver-v3-option-check")), "prompt v3");
    }
}
