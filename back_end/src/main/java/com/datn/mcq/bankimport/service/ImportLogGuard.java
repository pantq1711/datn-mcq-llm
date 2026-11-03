package com.datn.mcq.bankimport.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * Chặn log lỗi SQL của Hibernate ({@code org.hibernate.orm.jdbc.*}, {@code org.hibernate.engine.jdbc.*}: ghi nguyên thông
 * báo PostgreSQL kèm "Failing row contains (…)") CHỈ trong phạm vi một thao tác nhập từ Word (đánh dấu bằng MDC), để nội dung
 * bộ đề không vào log. Lỗi được {@link ImportDbErrors} log lại ở dạng an toàn (tên ràng buộc + mã lô). Ngoài phạm vi này
 * log của Hibernate giữ nguyên.
 *
 * <p>Logging của Spring Boot có thể khởi tạo lại LoggerContext (xóa turbo filter), nên mỗi lần mở phạm vi đều kiểm và cài
 * lại nếu thiếu.
 */
final class ImportLogGuard extends TurboFilter {

    static final String MDC_KEY = "bankImportBatch";
    /**
     * Logger lỗi JDBC của Hibernate: Hibernate 7 ghi ở {@code org.hibernate.orm.jdbc.error} (HHH000247 + nguyên thông báo
     * PostgreSQL), bản cũ ở {@code org.hibernate.engine.jdbc.spi.SqlExceptionHelper}.
     */
    private static final java.util.List<String> GUARDED_PREFIXES = java.util.List.of("org.hibernate.orm.jdbc",
            "org.hibernate.engine.jdbc");
    private static final ImportLogGuard INSTANCE = new ImportLogGuard();

    /** Phạm vi đang chặn; đóng thì bỏ đánh dấu (không ném ngoại lệ). */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private ImportLogGuard() {}

    static Scope open(Object batch) {
        ensureInstalled();
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, String.valueOf(batch));
        return () -> {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        };
    }

    private static synchronized void ensureInstalled() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx && !ctx.getTurboFilterList().contains(INSTANCE)) {
            if (!INSTANCE.isStarted()) {
                INSTANCE.start();
            }
            ctx.addTurboFilter(INSTANCE);
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (logger == null || MDC.get(MDC_KEY) == null) {
            return FilterReply.NEUTRAL;
        }
        String name = logger.getName();
        for (String prefix : GUARDED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
