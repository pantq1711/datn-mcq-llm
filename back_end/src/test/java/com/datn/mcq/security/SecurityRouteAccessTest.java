package com.datn.mcq.security;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-1: bảng quyền của MỌI endpoint /api/**. Endpoint mới chưa được xếp quyền ở đây thì test đỏ, để không có đường nào
 * vô tình mở công khai hoặc rơi vào denyAll ngoài ý muốn. Với mỗi endpoint: chưa đăng nhập, giáo viên và admin.
 *
 * <p>"Được qua" nghĩa là lớp bảo mật không chặn (không 403, không 401 do filter); handler có thể trả 400/404/409 vì dữ
 * liệu giả. Quyết định áp dụng: H4/H6 (admin không dùng chức năng giáo viên; sinh câu tính phí chỉ admin). Form chấm
 * /api/reviews/** đã gỡ ngày 25/10.
 */
@SpringBootTest(properties = "app.experiment.enabled=true")
@Import(TestSafetyConfiguration.class)
class SecurityRouteAccessTest {

    enum Access { PUBLIC, AUTHENTICATED, TEACHER, ADMIN, TEACHER_OR_ADMIN }

    private static final Map<String, Access> RULES = new LinkedHashMap<>();

    static {
        RULES.put("POST /api/auth/login", Access.PUBLIC);
        RULES.put("POST /api/auth/refresh", Access.PUBLIC);
        RULES.put("POST /api/auth/logout", Access.PUBLIC);
        RULES.put("GET /api/auth/me", Access.AUTHENTICATED);
        RULES.put("POST /api/auth/password", Access.AUTHENTICATED);

        RULES.put("POST /api/exam-matrices", Access.TEACHER);
        RULES.put("GET /api/exam-matrices", Access.TEACHER);
        RULES.put("GET /api/exam-matrices/{id}", Access.TEACHER);
        RULES.put("PUT /api/exam-matrices/{id}", Access.TEACHER);
        RULES.put("DELETE /api/exam-matrices/{id}", Access.TEACHER);
        RULES.put("POST /api/exam-matrices/{id}/duplicate", Access.TEACHER);
        RULES.put("GET /api/exam-matrices/samples", Access.TEACHER);
        RULES.put("POST /api/exam-matrices/samples/{key}", Access.TEACHER);

        RULES.put("GET /api/exams", Access.TEACHER);
        RULES.put("POST /api/exams/build", Access.TEACHER);
        RULES.put("POST /api/exams/build-by-lesson", Access.TEACHER);
        RULES.put("GET /api/exams/{examId}", Access.TEACHER);
        RULES.put("GET /api/exams/{examId}/progress", Access.TEACHER);
        RULES.put("GET /api/exams/{examId}/cells/{cellId}", Access.TEACHER);
        RULES.put("GET /api/exams/{examId}/export/docx", Access.TEACHER);
        RULES.put("POST /api/exams/{examId}/slots/{slotId}/keep", Access.TEACHER);
        RULES.put("POST /api/exams/{examId}/slots/{slotId}/replace", Access.TEACHER);
        RULES.put("PUT /api/exams/{examId}/slots/{slotId}/question", Access.TEACHER);

        RULES.put("GET /api/bank-items", Access.TEACHER_OR_ADMIN);
        RULES.put("GET /api/bank-items/{id}", Access.TEACHER_OR_ADMIN);
        RULES.put("GET /api/bank-items/topics", Access.TEACHER_OR_ADMIN);
        RULES.put("GET /api/exams/{examId}/slots/{slotId}/qc-trace", Access.TEACHER);
        RULES.put("GET /api/catalog/subjects", Access.TEACHER_OR_ADMIN);
        RULES.put("GET /api/catalog/templates", Access.TEACHER_OR_ADMIN);
        RULES.put("POST /api/catalog/templates", Access.ADMIN);

        RULES.put("GET /api/admin/users", Access.ADMIN);
        RULES.put("POST /api/admin/users", Access.ADMIN);
        RULES.put("PATCH /api/admin/users/{id}", Access.ADMIN);
        RULES.put("POST /api/admin/users/{id}/reset-password", Access.ADMIN);

        RULES.put("POST /api/generation/generate-sync", Access.ADMIN);
        RULES.put("POST /api/generation/jobs/enqueue", Access.ADMIN);
        RULES.put("POST /api/generation/jobs/process-next", Access.ADMIN);
        RULES.put("GET /api/generation/jobs", Access.ADMIN);
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int admin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "SecurityRouteAccessTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.route@test.vn", "teacher", "matkhau-dung-1", true);
        admin = AuthTestSupport.insertUser(jdbc, "Admin", "admin.route@test.vn", "admin", "matkhau-admin-12", true);
    }

    /** Mọi cặp "METHOD /api/..." mà ứng dụng đang phục vụ. */
    private Set<String> apiEndpoints() {
        Set<String> out = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues() : info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                assertFalse(methods.isEmpty(), "Endpoint phải khai báo HTTP method: " + pattern);
                for (RequestMethod m : methods) {
                    out.add(m.name() + " " + pattern);
                }
            }
        }
        return out;
    }

    @Test
    void moiEndpointApiDeuDuocXepQuyen() {
        Set<String> actual = apiEndpoints();
        Set<String> missing = new TreeSet<>(actual);
        missing.removeAll(RULES.keySet());
        Set<String> stale = new TreeSet<>(RULES.keySet());
        stale.removeAll(actual);
        assertTrue(missing.isEmpty(), "Endpoint chưa được xếp quyền trong test: " + missing);
        assertTrue(stale.isEmpty(), "Quy tắc cho endpoint không còn tồn tại: " + stale);
    }

    /** Kết quả gọi: mã HTTP và "code" trong JSON lỗi (nếu có). */
    record Outcome(int status, String code) {
        boolean blockedBySecurity() {
            return status == 403 || (status == 401 && code != null
                    && (code.equals("UNAUTHENTICATED") || code.startsWith("TOKEN_")));
        }
    }

    private Outcome call(String endpoint, RequestPostProcessor who) throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String url = parts[1].replaceAll("\\{[^}]+}", "999999");
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(parts[0]), url);
        if (parts[0].equals("POST") || parts[0].equals("PATCH") || parts[0].equals("PUT")) {
            req.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (who != null) {
            req.with(who);
        }
        try {
            MockHttpServletResponse r = mockMvc.perform(req).andReturn().getResponse();
            String code = null;
            String body = r.getContentAsString(StandardCharsets.UTF_8);
            if (body.startsWith("{")) {
                JsonNode node = objectMapper.readTree(body);
                code = node.hasNonNull("code") ? node.get("code").asText() : null;
            }
            return new Outcome(r.getStatus(), code);
        } catch (Exception handlerError) {
            // Lỗi ném ra từ handler (dữ liệu giả, LLM bị chặn trong test): đã qua lớp bảo mật.
            return new Outcome(500, null);
        }
    }

    @Test
    void phanQuyenDungTheoBang() throws Exception {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Access> rule : RULES.entrySet()) {
            String endpoint = rule.getKey();
            Access access = rule.getValue();

            Outcome anon = call(endpoint, null);
            if (access == Access.PUBLIC) {
                if (anon.blockedBySecurity()) {
                    errors.add(endpoint + ": công khai nhưng bị chặn " + anon);
                }
            } else if (!(anon.status() == 401 && "UNAUTHENTICATED".equals(anon.code()))) {
                errors.add(endpoint + ": chưa đăng nhập phải 401 UNAUTHENTICATED, nhận " + anon);
            }

            boolean teacherAllowed = access != Access.ADMIN;
            boolean adminAllowed = access != Access.TEACHER;
            check(errors, endpoint, "giáo viên", call(endpoint, AuthTestSupport.teacher(teacher)), teacherAllowed);
            check(errors, endpoint, "admin", call(endpoint, AuthTestSupport.admin(admin)), adminAllowed);
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    private static void check(List<String> errors, String endpoint, String who, Outcome outcome, boolean allowed) {
        if (allowed && outcome.blockedBySecurity()) {
            errors.add(endpoint + ": " + who + " phải được qua, nhận " + outcome);
        } else if (!allowed && outcome.status() != 403) {
            errors.add(endpoint + ": " + who + " phải 403, nhận " + outcome);
        }
    }

    @Test
    void duongDanLaBiChanMacDinh() throws Exception {
        assertEquals(401, call("GET /api/khong-ton-tai", null).status());
        assertEquals(403, call("GET /api/khong-ton-tai", AuthTestSupport.teacher(teacher)).status());
        assertEquals(403, call("GET /api/admin/khong-ton-tai", AuthTestSupport.teacher(teacher)).status());
    }

    @Test
    void healthCongKhaiNhungKhongLoChiTiet() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andReturn().getResponse();
        JsonNode body = objectMapper.readTree(r.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("UP", body.get("status").asText());
        assertFalse(body.has("components"), "show-details: when-authorized, người lạ không thấy chi tiết DB");
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
