package com.datn.mcq.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bảng điểm (V4): mặc định đúng đề và ma trận thật; file application.yaml của main khai đúng các giá trị đó;
 * cấu hình sai bị từ chối khi nạp. Không dựng Spring context, không chạm DB.
 */
class ExamScoringPropertiesTest {

    private static final String PREFIX = "app.exam.scoring";

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "mong đợi " + expected + ", nhận " + actual);
    }

    private static void assertMatrixValues(ExamScoringProperties p) {
        assertDecimal("0.25", p.mcqPerQuestion());
        assertDecimal("0.5", p.saPerQuestion());
        assertDecimal("0", p.tfScore(0));
        assertDecimal("0.1", p.tfScore(1));
        assertDecimal("0.25", p.tfScore(2));
        assertDecimal("0.5", p.tfScore(3));
        assertDecimal("1.0", p.tfScore(4));
    }

    @Test
    void macDinhKhopDeVaMaTran() {
        ExamScoringProperties p = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate(PREFIX, ExamScoringProperties.class);
        assertMatrixValues(p);
        assertThrows(IllegalArgumentException.class, () -> p.tfScore(5));
        assertThrows(IllegalArgumentException.class, () -> p.tfScore(-1));
    }

    @Test
    void applicationYamlCuaMainKhaiDungGiaTri() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("main-application", new FileSystemResource("src/main/resources/application.yaml"));
        ExamScoringProperties p = new Binder(ConfigurationPropertySources.from(sources))
                .bind(PREFIX, ExamScoringProperties.class)
                .orElseThrow(() -> new AssertionError("application.yaml của main thiếu " + PREFIX));
        assertMatrixValues(p);
    }

    @Test
    void cauHinhSaiBiTuChoi() {
        Map<String, String> fourValues = Map.of(
                PREFIX + ".tf-by-correct-count[0]", "0",
                PREFIX + ".tf-by-correct-count[1]", "0.1",
                PREFIX + ".tf-by-correct-count[2]", "0.25",
                PREFIX + ".tf-by-correct-count[3]", "1.0");
        assertThrows(BindException.class, () -> new Binder(new MapConfigurationPropertySource(fourValues))
                .bind(PREFIX, ExamScoringProperties.class));

        Map<String, String> decreasing = Map.of(
                PREFIX + ".tf-by-correct-count[0]", "0",
                PREFIX + ".tf-by-correct-count[1]", "0.5",
                PREFIX + ".tf-by-correct-count[2]", "0.25",
                PREFIX + ".tf-by-correct-count[3]", "0.5",
                PREFIX + ".tf-by-correct-count[4]", "1.0");
        assertThrows(BindException.class, () -> new Binder(new MapConfigurationPropertySource(decreasing))
                .bind(PREFIX, ExamScoringProperties.class));
    }
}
