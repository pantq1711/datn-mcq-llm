package com.datn.mcq.assignment;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1 (docs/design/cau-co-hinh.md mục 4.1, bảng 4.5; D1 = A, D6 = private): học sinh xem hình của câu qua URL ký theo lượt
 * làm, {@code sig = HMAC(khóa ảnh, "p|id|exp|attemptId")}. Mọi request ảnh ở đây KHÔNG kèm JWT. A1 còn chặn giao bài có
 * hình nên ảnh được gắn thẳng bằng SQL vào câu nguồn SAU khi đã giao bài (dữ liệu tự tạo, không phải ảnh bộ đề).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class PublicImageApiTest extends AssignmentTestBase {

    @Value("${app.question-images.url-signing-key}")
    String signingKey;

    // ---------- tiện ích ----------

    private String hmac(String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(signingKey.trim()), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    /** URL tự ký đúng hợp đồng S1 (dùng cho ảnh không có trong JSON, như ảnh lời giải lúc làm bài). */
    private String signedUrl(long imageId, long exp, int attempt) throws Exception {
        return url(imageId, exp, attempt, hmac("p|" + imageId + "|" + exp + "|" + attempt));
    }

    private static String url(long imageId, long exp, int attempt, String sig) {
        return "/api/public/question-images/" + imageId + "?exp=" + exp + "&a=" + attempt + "&sig=" + sig;
    }

    private static MultiValueMap<String, String> query(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams();
    }

    private ResultActions load(String url) throws Exception {
        return mockMvc.perform(get(url));
    }

    private void expectNotFound(String url) throws Exception {
        load(url).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    /** Câu {@code index} của Phần {@code part} trong {@code parts} (P2/P3) hoặc {@code review}. */
    private static JsonNode question(JsonNode partsOrReview, int part, int index) {
        return partsOrReview.get(part).get("items").get(index);
    }

    private static List<Long> imageIds(JsonNode question) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode img : question.get("images")) {
            ids.add(img.get("id").asLong());
        }
        return ids;
    }

    private static String imageUrl(JsonNode question, long imageId) {
        for (JsonNode img : question.get("images")) {
            if (img.get("id").asLong() == imageId) {
                return img.get("url").asText();
            }
        }
        throw new AssertionError("Không có ảnh " + imageId + " trong " + question);
    }

    private static long epoch(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    // ---------- JSON P2/P3 ----------

    @Test
    void partsCarrySignedImagesButNeverTheSolutionImage() throws Exception {
        String code = assign(false, true).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        long optionC = attachImage(mcqItem1, "option_c", 2);
        long solution = attachImage(mcqItem1, "solution", 3);
        long statementB = attachImage(tfItem, "statement_b", 1);

        JsonNode started = json(start(code, "8A1", 1));
        int attempt = attemptId("8A1", 1);
        JsonNode parts = started.get("parts");
        JsonNode mcq1 = question(parts, 0, 0);
        assertEquals(List.of(stem, optionC), imageIds(mcq1), "ảnh lời giải không được có trong images khi làm bài");
        assertFalse(started.toString().contains("\"id\":" + solution));
        assertFalse(question(parts, 0, 1).has("images"), "câu không có hình: không có trường images");
        assertFalse(question(parts, 2, 0).has("images"));
        assertEquals(List.of(statementB), imageIds(question(parts, 1, 0)));

        JsonNode img = mcq1.get("images").get(0);
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = img.fieldNames(); it.hasNext(); ) {
            keys.add(it.next());
        }
        assertEquals(List.of("id", "mime", "widthPx", "heightPx", "displayWidthMm", "url"), keys,
                "PublicImageDto: không có role, sha256, kích thước byte");
        assertEquals("image/png", img.get("mime").asText());
        assertEquals(50, img.get("displayWidthMm").asInt());

        // exp = hạn của lượt + ân hạn 2 phút; a = id lượt; chữ ký theo tiền tố p|.
        String stemUrl = img.get("url").asText();
        assertTrue(stemUrl.startsWith("/api/public/question-images/" + stem + "?"), stemUrl);
        MultiValueMap<String, String> q = query(stemUrl);
        long exp = Long.parseLong(q.getFirst("exp"));
        assertEquals(epoch(LocalDateTime.parse(started.get("deadlineAt").asText()).plusMinutes(2)), exp);
        assertEquals(String.valueOf(attempt), q.getFirst("a"));
        assertEquals(hmac("p|" + stem + "|" + exp + "|" + attempt), q.getFirst("sig"));

        // P3: URL giữ nguyên trong cả lượt (cache trình duyệt dùng lại được).
        String token = started.get("attemptToken").asText();
        JsonNode again = json(current(token));
        assertEquals(stemUrl, imageUrl(question(again.get("parts"), 0, 0), stem));
    }

    @Test
    void signedUrlLoadsWithoutJwtWithPrivateCacheHeaders() throws Exception {
        String code = assign(false, false).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        String stemUrl = imageUrl(question(json(start(code, "8A1", 1)).get("parts"), 0, 0), stem);

        MvcResult ok = load(stemUrl)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + "c".repeat(64) + "\""))
                .andReturn();
        assertArrayEquals(FAKE_PNG, ok.getResponse().getContentAsByteArray());
        String cache = ok.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
        assertNotNull(cache);
        assertTrue(cache.startsWith("private, max-age="), cache);
        long maxAge = Long.parseLong(cache.substring("private, max-age=".length()));
        assertTrue(maxAge > 0 && maxAge <= 3600, cache);

        mockMvc.perform(get(stemUrl).header(HttpHeaders.IF_NONE_MATCH, "\"" + "c".repeat(64) + "\""))
                .andExpect(status().isNotModified());
    }

    // ---------- chữ ký ----------

    @Test
    void wrongExpiredOrAlteredSignatureIsNotFound() throws Exception {
        String code = assign(false, false).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        long optionC = attachImage(mcqItem1, "option_c", 2);
        String stemUrl = imageUrl(question(json(start(code, "8A1", 1)).get("parts"), 0, 0), stem);
        start(code, "8A1", 2);
        int attempt = attemptId("8A1", 1);
        int otherAttempt = attemptId("8A1", 2);
        MultiValueMap<String, String> q = query(stemUrl);
        long exp = Long.parseLong(q.getFirst("exp"));
        String sig = q.getFirst("sig");
        load(stemUrl).andExpect(status().isOk());

        String flipped = (sig.charAt(0) == '0' ? "1" : "0") + sig.substring(1);
        expectNotFound(url(stem, exp, attempt, flipped));
        expectNotFound(url(optionC, exp, attempt, sig));          // đổi id
        expectNotFound(url(stem, exp, otherAttempt, sig));        // đổi a (lượt khác vẫn tồn tại)
        expectNotFound(url(stem, exp + 1, attempt, sig));         // đổi exp
        expectNotFound(signedUrl(stem, Instant.now().getEpochSecond() - 5, attempt));   // ký đúng nhưng hết hạn

        // Thiếu hoặc sai kiểu tham số.
        expectNotFound("/api/public/question-images/" + stem + "?exp=" + exp + "&a=" + attempt);
        expectNotFound("/api/public/question-images/" + stem + "?a=" + attempt + "&sig=" + sig);
        expectNotFound("/api/public/question-images/x?exp=" + exp + "&a=" + attempt + "&sig=" + sig);
        expectNotFound("/api/public/question-images/" + stem + "?exp=" + exp + "&a=abc&sig=" + sig);

        // Tách miền chữ ký: chữ ký kiểu giáo viên (không tiền tố p|) không mở được ảnh học sinh, và ngược lại.
        expectNotFound(url(stem, exp, attempt, hmac(stem + "|" + exp + "|" + attempt)));
        load("/api/question-images/" + stem + "?exp=" + exp + "&u=" + attempt + "&sig=" + sig)
                .andExpect(status().isNotFound());
    }

    // ---------- điều kiện hiển thị ----------

    @Test
    void deletedAttemptCannotLoadImages() throws Exception {
        String code = assign(false, false).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        String stemUrl = imageUrl(question(json(start(code, "8A1", 1)).get("parts"), 0, 0), stem);
        load(stemUrl).andExpect(status().isOk());

        jdbc.update("DELETE FROM assignment_attempt WHERE id = ?", attemptId("8A1", 1));
        expectNotFound(stemUrl);
    }

    @Test
    void imageOfAQuestionOutsideTheAssignmentIsNotFound() throws Exception {
        String code = assign(false, false).get("publicCode").asText();
        start(code, "8A1", 1);
        int outside = bankMcq(template("Ngoài đề"), "Câu không có trong bài giao");
        long foreign = attachImage(outside, "stem", 1);
        Instant now = Instant.now();
        expectNotFound(signedUrl(foreign, now.getEpochSecond() + 600, attemptId("8A1", 1)));
    }

    @Test
    void solutionImageIsNotFoundWhileTheAttemptIsOpen() throws Exception {
        String code = assign(false, true).get("publicCode").asText();
        attachImage(mcqItem1, "stem", 1);
        long solution = attachImage(mcqItem1, "solution", 2);
        JsonNode started = json(start(code, "8A1", 1));
        long exp = Long.parseLong(query(started.get("parts").get(0).get("items").get(0).get("images").get(0)
                .get("url").asText()).getFirst("exp"));
        expectNotFound(signedUrl(solution, exp, attemptId("8A1", 1)));
    }

    @Test
    void submittedAttemptLoadsNoImagesBeforeResults() throws Exception {
        String code = assign(false, true).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        JsonNode started = json(start(code, "8A1", 1));
        String stemUrl = imageUrl(question(started.get("parts"), 0, 0), stem);
        submit(started.get("attemptToken").asText(), sampleAnswers()).andExpect(status().isOk());
        // JSON không còn parts sau khi nộp nên ảnh cũng không còn.
        expectNotFound(stemUrl);
    }

    @Test
    void afterGraceSolutionImageLoadsOnlyWhenShowSolutionsIsOn() throws Exception {
        JsonNode a = assign(false, true);
        long stem = attachImage(mcqItem1, "stem", 1);
        long solution = attachImage(mcqItem1, "solution", 2);
        String token = json(start(a.get("publicCode").asText(), "8A1", 1)).get("attemptToken").asText();
        submit(token, sampleAnswers()).andExpect(status().isOk());
        closeAndPassGrace(a.get("assignmentId").asInt());

        JsonNode released = json(current(token));
        assertTrue(released.get("resultsReleased").asBoolean());
        JsonNode mcq1 = question(released.get("review"), 0, 0);
        assertEquals(List.of(stem, solution), imageIds(mcq1), "xem lại có ảnh lời giải");
        assertFalse(question(released.get("review"), 0, 1).has("images"));
        String solutionUrl = imageUrl(mcq1, solution);
        long exp = Long.parseLong(query(solutionUrl).getFirst("exp"));
        long now = Instant.now().getEpochSecond();
        assertTrue(exp > now && exp <= now + 2 * 3600, "xem lại: hết giờ tròn kế tiếp + 1 giờ, exp = " + exp);
        load(solutionUrl).andExpect(status().isOk());
        load(imageUrl(mcq1, stem)).andExpect(status().isOk());
    }

    @Test
    void afterGraceWithoutShowSolutionsNoImageLoads() throws Exception {
        JsonNode a = assign(true, false);
        long stem = attachImage(mcqItem1, "stem", 1);
        long solution = attachImage(mcqItem1, "solution", 2);
        JsonNode started = json(start(a.get("publicCode").asText(), "8A1", 1));
        String stemUrl = imageUrl(question(started.get("parts"), 0, 0), stem);
        String token = started.get("attemptToken").asText();
        submit(token, sampleAnswers()).andExpect(status().isOk());
        closeAndPassGrace(a.get("assignmentId").asInt());

        JsonNode released = json(current(token));
        assertTrue(released.get("resultsReleased").asBoolean());
        assertFalse(released.has("review"));
        assertFalse(released.toString().contains("question-images"));
        int attempt = attemptId("8A1", 1);
        expectNotFound(signedUrl(solution, Instant.now().getEpochSecond() + 600, attempt));
        expectNotFound(stemUrl);
    }

    @Test
    void imageOfAQuestionRemovedFromTheBankStillLoads() throws Exception {
        String code = assign(false, false).get("publicCode").asText();
        long stem = attachImage(mcqItem1, "stem", 1);
        // Quyết định 16/11: câu gỡ khỏi ngân hàng vẫn hiện ở bài đã giao, nên ảnh cũng vậy (không dùng hasAccessPermission).
        jdbc.update("UPDATE bank_item SET removed_at = CURRENT_TIMESTAMP, removed_by = ? WHERE id = ?", teacher,
                mcqItem1);
        String stemUrl = imageUrl(question(json(start(code, "8A1", 1)).get("parts"), 0, 0), stem);
        load(stemUrl).andExpect(status().isOk());
    }
}
