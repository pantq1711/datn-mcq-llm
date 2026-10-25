package com.datn.mcq.config;

import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bảo vệ test guard: BlockingTestLlmClient phải chặn MỌI method public của LlmClient có khả năng gọi mạng.
 *
 * <p>Constructor của BlockingTestLlmClient truyền một API key không rỗng, nên nếu thiếu override thì
 * method đó sẽ gửi request HTTP thật tới Gemini. Test này thất bại khi ai đó thêm method public mới vào
 * LlmClient mà chưa chặn trong BlockingTestLlmClient (hoặc chưa phân loại vào danh sách an toàn bên dưới).
 *
 * <p>Test không gọi mạng: chỉ đọc khai báo method bằng reflection; chỉ gọi thử những method đã được
 * BlockingTestLlmClient override.
 */
class BlockingTestLlmClientGuardTest {

    /**
     * Các method public của LlmClient KHÔNG gọi mạng (chỉ đọc cấu hình). Thêm vào đây phải có lý do rõ ràng
     * và được người duyệt xác nhận; mặc định method mới của LlmClient bị coi là có thể gọi mạng.
     */
    private static final Set<String> NON_NETWORK_METHODS = Set.of("getDefaultModel");

    /** Method đọc cấu hình nhưng phải bị ép trả về giá trị an toàn trong test. */
    private static final Set<String> MUST_BE_OVERRIDDEN_CONFIG_METHODS = Set.of("isConfigured");

    private static List<Method> publicInstanceMethodsOfLlmClient() {
        return Arrays.stream(LlmClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isSynthetic())
                .toList();
    }

    private static List<Method> potentiallyNetworkMethods() {
        return publicInstanceMethodsOfLlmClient().stream()
                .filter(m -> !NON_NETWORK_METHODS.contains(m.getName()))
                .toList();
    }

    private static boolean declaredIn(Class<?> type, Method m) {
        try {
            type.getDeclaredMethod(m.getName(), m.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    void blockingClientMustOverrideEveryPublicLlmClientMethodThatMayReachTheNetwork() {
        List<Method> candidates = potentiallyNetworkMethods();
        assertFalse(candidates.isEmpty(), "Không tìm thấy method public nào của LlmClient (test hỏng?)");

        List<String> missing = candidates.stream()
                .filter(m -> !declaredIn(TestSafetyConfiguration.BlockingTestLlmClient.class, m))
                .map(Method::toGenericString)
                .toList();

        assertTrue(missing.isEmpty(),
                "BlockingTestLlmClient chưa chặn các method có thể gọi Gemini thật: " + missing);
    }

    @Test
    void everyOverriddenNetworkMethodMustThrowUnsupportedOperationInsteadOfCallingOut() throws Exception {
        TestSafetyConfiguration.BlockingTestLlmClient blocking = new TestSafetyConfiguration.BlockingTestLlmClient();

        int invoked = 0;
        for (Method m : potentiallyNetworkMethods()) {
            // Chỉ gọi thử method đã được override, để test không bao giờ tự phát sinh request thật
            if (!declaredIn(TestSafetyConfiguration.BlockingTestLlmClient.class, m)) {
                continue;
            }
            if (MUST_BE_OVERRIDDEN_CONFIG_METHODS.contains(m.getName())) {
                continue;
            }
            Object[] args = Arrays.stream(m.getParameterTypes())
                    .map(BlockingTestLlmClientGuardTest::dummyArgument)
                    .toArray();
            InvocationTargetException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    InvocationTargetException.class,
                    () -> m.invoke(blocking, args),
                    "Method " + m.getName() + " phải ném lỗi");
            assertInstanceOf(UnsupportedOperationException.class, ex.getCause(),
                    "Method " + m.getName() + " phải ném UnsupportedOperationException");
            assertTrue(String.valueOf(ex.getCause().getMessage()).contains("BLOCKED"));
            invoked++;
        }
        assertTrue(invoked >= 2, "Kỳ vọng ít nhất callJsonMode, callJsonModeWithSchema bị chặn, thực tế: "
                + invoked);
    }

    @Test
    void blockingClientMustReportNotConfiguredSoCallersNeverTryTheNetwork() {
        TestSafetyConfiguration.BlockingTestLlmClient blocking = new TestSafetyConfiguration.BlockingTestLlmClient();
        assertFalse(blocking.isConfigured());
        for (String name : MUST_BE_OVERRIDDEN_CONFIG_METHODS) {
            boolean overridden = Arrays.stream(TestSafetyConfiguration.BlockingTestLlmClient.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().equals(name));
            assertTrue(overridden, "BlockingTestLlmClient phải override " + name);
        }
    }

    @Test
    void networkReturningMethodsAreAmongTheCandidatesSoTheGuardCannotPassVacuously() {
        Set<String> names = potentiallyNetworkMethods().stream()
                .filter(m -> m.getReturnType() == LlmCallResult.class)
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("callJsonMode", "callJsonModeWithSchema"), names,
                "Danh sách method gọi mạng của LlmClient đã thay đổi: cập nhật BlockingTestLlmClient và test này");
    }

    private static Object dummyArgument(Class<?> type) {
        if (type == String.class) {
            return "x";
        }
        if (type == Double.class) {
            return 0.0;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        return null;
    }
}
