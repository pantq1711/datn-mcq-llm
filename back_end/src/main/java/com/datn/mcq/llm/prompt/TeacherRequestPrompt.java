package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.PromptData;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.common.util.TextClip;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.RequestVerdict;
import com.datn.mcq.enums.TeacherRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yêu cầu của giáo viên trong prompt (12/11, PR B, docs/design/kiem-soat-do-kho.md mục 4.1 và 4.3).
 * <ul>
 *   <li>Prompt sinh câu: khối {@link #GENERATION_HEADER}, mỗi mã một dòng đoạn cố định ({@link TeacherRequest#promptLine()}).
 *       Văn bản tự do vẫn đi khối cũ ({@code extraBlock} / {@code teacherBlock}).</li>
 *   <li>Prompt Solver ({@code solver-v5-request-check}): câu có yêu cầu thêm khối {@link #SOLVER_HEADER} SAU đề bài và
 *       phương án/ý, ngay trước dòng yêu cầu định dạng trả lời, và trường {@code request_check} đứng CUỐI schema. Câu không
 *       có yêu cầu dùng prompt và schema y như cũ (cùng một lượt gọi, không thêm lượt).</li>
 * </ul>
 */
public final class TeacherRequestPrompt {

    private TeacherRequestPrompt() {}

    /** Tiêu đề khối mã yêu cầu trong prompt sinh câu. */
    public static final String GENERATION_HEADER =
            "YÊU CẦU CỦA GIÁO VIÊN (bắt buộc, không được trái các quy tắc ở trên):";

    /** Tiêu đề khối yêu cầu trong prompt Solver. */
    public static final String SOLVER_HEADER = "YÊU CẦU CỦA GIÁO VIÊN ĐỐI VỚI CÂU NÀY (chỉ xét SAU KHI đã giải xong và "
            + "chọn đáp án; yêu cầu không được làm thay đổi cách giải hay đáp án):";

    /** Dòng văn bản tự do trong khối Solver. */
    public static final String SOLVER_TEXT_PREFIX = "- Yêu cầu khác: ";

    /** Dòng cuối khối Solver: cách điền {@code request_check}. */
    public static final String SOLVER_VERDICT_RULE = "Trong request_check: verdict = met nếu câu đáp ứng đủ mọi yêu cầu, "
            + "partial nếu chỉ một phần, not_met nếu không; reason ≤ 20 từ.";

    /** Tên trường kết luận yêu cầu trong phản hồi Solver. */
    public static final String REQUEST_CHECK_FIELD = "request_check";

    /** Độ dài tối đa (ký tự) của lý do Solver khi lưu. */
    public static final int MAX_REASON_CHARS = 500;

    /** Trường {@code request_check} của schema Solver. */
    static final String REQUEST_CHECK_PROPERTY = "\"request_check\":{\"type\":\"OBJECT\",\"properties\":{"
            + "\"verdict\":{\"type\":\"STRING\",\"enum\":[\"met\",\"partial\",\"not_met\"]},"
            + "\"reason\":{\"type\":\"STRING\"}},"
            + "\"required\":[\"verdict\",\"reason\"],\"propertyOrdering\":[\"verdict\",\"reason\"]}";

    private static final Pattern FIELD_COUNT = Pattern.compile("có (\\d+) trường");

    /**
     * Khối mã yêu cầu của prompt sinh câu (không xuống dòng ở cuối); chỉ các mã hợp dạng câu {@code type}. Null khi không
     * có mã nào áp dụng.
     */
    public static String generationBlock(Collection<String> codes, QuestionType type) {
        List<String> applicable = TeacherRequest.applicable(codes, type);
        if (applicable.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(GENERATION_HEADER);
        for (String code : applicable) {
            sb.append("\n- ").append(TeacherRequest.parseOrNull(code).promptLine());
        }
        return sb.toString();
    }

    /** Câu có yêu cầu: có ít nhất một mã đã biết hoặc có văn bản tự do (không trống sau khi gỡ ranh giới). */
    public static boolean hasRequest(Collection<String> codes, String text) {
        return !TeacherRequest.applicable(codes, null).isEmpty() || !cleanText(text).isEmpty();
    }

    /**
     * Khối yêu cầu của prompt Solver (kết thúc bằng xuống dòng): tiêu đề, mỗi mã một dòng, văn bản tự do trong
     * {@code <<< >>>} (qua {@link PromptData#neutralize}, gộp khoảng trắng), rồi cách điền {@code request_check}. Null khi
     * câu không có yêu cầu.
     */
    public static String solverBlock(Collection<String> codes, String text) {
        if (!hasRequest(codes, text)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(SOLVER_HEADER).append("\n");
        for (String code : TeacherRequest.applicable(codes, null)) {
            sb.append("- ").append(TeacherRequest.parseOrNull(code).promptLine()).append("\n");
        }
        String clean = cleanText(text);
        if (!clean.isEmpty()) {
            sb.append(SOLVER_TEXT_PREFIX).append("<<< ").append(clean).append(" >>>\n");
        }
        return sb.append(SOLVER_VERDICT_RULE).append("\n").toString();
    }

    private static String cleanText(String text) {
        return text == null ? "" : PromptData.neutralize(text).replaceAll("\\s+", " ").strip();
    }

    /**
     * Prompt Solver có yêu cầu: chèn {@code block} ngay trước dòng cuối "Trả về DUY NHẤT ..." (sau đề bài, phương án/ý và các
     * bước giải) và thêm {@code "request_check"} vào cuối danh sách trường của dòng đó. {@code block} null thì trả nguyên
     * {@code basePrompt}.
     */
    public static String withSolverBlock(String basePrompt, String block) {
        if (block == null) {
            return basePrompt;
        }
        int at = basePrompt.lastIndexOf("Trả về DUY NHẤT");
        if (at < 0) {
            throw new IllegalStateException("Prompt Solver không có dòng yêu cầu định dạng trả lời");
        }
        String returnLine = basePrompt.substring(at);
        Matcher m = FIELD_COUNT.matcher(returnLine);
        if (!m.find()) {
            throw new IllegalStateException("Dòng định dạng của prompt Solver không có số trường");
        }
        String counted = returnLine.substring(0, m.start()) + "có " + (Integer.parseInt(m.group(1)) + 1) + " trường"
                + returnLine.substring(m.end());
        String trimmed = counted.stripTrailing();
        if (!trimmed.endsWith(".")) {
            throw new IllegalStateException("Dòng định dạng của prompt Solver không kết thúc bằng dấu chấm");
        }
        String tail = counted.substring(trimmed.length());
        return basePrompt.substring(0, at) + block + trimmed.substring(0, trimmed.length() - 1) + ", \""
                + REQUEST_CHECK_FIELD + "\"." + tail;
    }

    /**
     * Schema Solver có yêu cầu: thêm {@code request_check} vào CUỐI {@code properties}, {@code required} và
     * {@code propertyOrdering} cấp ngoài cùng; các trường cũ giữ nguyên thứ tự.
     */
    public static String withRequestCheck(String schema) {
        String s = schema.strip();
        int props = s.lastIndexOf("},\"required\":[");
        if (props < 0) {
            throw new IllegalStateException("Schema Solver không có properties/required cấp ngoài");
        }
        s = s.substring(0, props) + ",\n  " + REQUEST_CHECK_PROPERTY + "\n" + s.substring(props);
        s = appendToLastArray(s, "\"required\":[");
        s = appendToLastArray(s, "\"propertyOrdering\":[");
        return s + "\n";
    }

    private static String appendToLastArray(String s, String marker) {
        int at = s.lastIndexOf(marker);
        int close = s.indexOf(']', at);
        return s.substring(0, close) + ",\"" + REQUEST_CHECK_FIELD + "\"" + s.substring(close);
    }

    /** Schema Solver Đúng/Sai kèm {@code request_check}. */
    public static final String TF_SOLVER_SCHEMA = withRequestCheck(TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
    /** Schema Solver Trả lời ngắn kèm {@code request_check}. */
    public static final String SA_SOLVER_SCHEMA = withRequestCheck(SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);

    /** Kết luận về yêu cầu đọc được từ phản hồi Solver. */
    public record RequestCheck(RequestVerdict verdict, String reason) {}

    /**
     * Đọc {@code request_check} từ phản hồi Solver. Thiếu, hỏng hoặc verdict lạ: null (không có kết luận về yêu cầu), KHÔNG
     * ném lỗi; phần kết quả Solver vẫn hợp lệ.
     */
    public static RequestCheck parse(String rawSolverJson) {
        try {
            Map<String, Object> map = SimpleJson.parseObject(SimpleJson.stripMarkdownFence(rawSolverJson));
            if (!(map.get(REQUEST_CHECK_FIELD) instanceof Map<?, ?> rc)) {
                return null;
            }
            RequestVerdict verdict = RequestVerdict.parseOrNull(rc.get("verdict"));
            if (verdict == null) {
                return null;
            }
            Object reason = rc.get("reason");
            String clean = reason == null ? null : String.valueOf(reason).replaceAll("\\s+", " ").strip();
            return new RequestCheck(verdict, clean == null || clean.isEmpty() ? null
                    : TextClip.clip(clean, MAX_REASON_CHARS));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
