package com.datn.mcq.bankimport.service;

import com.datn.mcq.bankimport.entity.ImportBatch;
import com.datn.mcq.bankimport.repository.ImportBatchRepository;
import com.datn.mcq.common.exception.ApiErrorException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu bản xem trước của một lần nhập: kiểm trần số lô chờ xác nhận và lưu lô trong CÙNG transaction, có khóa
 * {@code pg_advisory_xact_lock(LOCK_SPACE, ownerId)} theo người tải, để hai lần tải đồng thời của cùng giáo viên không cùng
 * lọt trần. Khóa nhả khi transaction kết thúc.
 *
 * <p>Khóa dạng hai số int với {@link #LOCK_SPACE} cố định riêng cho nhập từ Word; không gian khóa hai-int của PostgreSQL
 * tách biệt với không gian một-bigint, và dự án chưa có khóa advisory nào khác (3/11).
 */
@Component
public class ImportBatchWriter {

    /** "BIMP" (bank import) dạng int: phần đầu của khóa advisory, chỉ dùng cho nhập từ Word. */
    static final int LOCK_SPACE = 0x42494D50;

    private final ImportBatchRepository batches;
    private final JdbcTemplate jdbc;
    private final BankImportProperties props;

    public ImportBatchWriter(ImportBatchRepository batches, JdbcTemplate jdbc, BankImportProperties props) {
        this.batches = batches;
        this.jdbc = jdbc;
        this.props = props;
    }

    /**
     * @throws ApiErrorException 409 IMPORT_PENDING_LIMIT khi đã đủ lô chờ; 422 IMPORT_DATA_INVALID / 409 IMPORT_CONFLICT khi
     *                           vi phạm ràng buộc DB (không log nội dung)
     */
    @Transactional
    public ImportBatch savePreview(ImportBatch b) {
        try (ImportLogGuard.Scope ignored = ImportLogGuard.open("mới")) {
            jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null, LOCK_SPACE, b.getOwnerId());
            if (batches.countByOwnerIdAndStatusAndExpiresAtAfter(b.getOwnerId(), ImportBatch.STATUS_PREVIEW,
                    b.getCreatedAt()) >= props.getMaxPendingBatches()) {
                throw BankImportService.pendingLimit(props.getMaxPendingBatches());
            }
            try {
                return batches.saveAndFlush(b);
            } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException e) {
                ApiErrorException safe = ImportDbErrors.translate(e, "lưu", "mới");
                if (safe == null) {
                    throw e;
                }
                throw safe;
            }
        }
    }
}
