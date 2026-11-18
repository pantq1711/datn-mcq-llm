package com.datn.mcq.bankimport.service;

import com.datn.mcq.bankimport.dto.BankImportDtos.ImportItem;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemOption;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemStatement;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.entity.ImportBatch;
import com.datn.mcq.bankimport.model.NormalizedImageRef;
import com.datn.mcq.bankimport.repository.ImportBatchRepository;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.questionimage.dto.QuestionImageDto;
import com.datn.mcq.questionimage.model.QuestionImage;
import com.datn.mcq.questionimage.repository.QuestionImageRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lưu bản xem trước của một lần nhập: kiểm trần số lô chờ xác nhận và lưu lô trong CÙNG transaction, có khóa
 * {@code pg_advisory_xact_lock(LOCK_SPACE, ownerId)} theo người tải, để hai lần tải đồng thời của cùng giáo viên không cùng
 * lọt trần. Khóa nhả khi transaction kết thúc.
 */
@Component
public class ImportBatchWriter {

    /** "BIMP" (bank import) dạng int: phần đầu của khóa advisory, chỉ dùng cho nhập từ Word. */
    static final int LOCK_SPACE = 0x42494D50;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ImportBatchRepository batches;
    private final QuestionImageRepository questionImageRepo;
    private final JdbcTemplate jdbc;
    private final BankImportProperties props;

    public ImportBatchWriter(ImportBatchRepository batches, QuestionImageRepository questionImageRepo,
                             JdbcTemplate jdbc, BankImportProperties props) {
        this.batches = batches;
        this.questionImageRepo = questionImageRepo;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record PreviewSaveResult(ImportBatch batch, PreviewData preview) {}

    @Transactional
    public PreviewSaveResult savePreview(ImportBatch b, PreviewData preview, Map<String, List<NormalizedImageRef>> imagesByKey) {
        try (ImportLogGuard.Scope ignored = ImportLogGuard.open("mới")) {
            jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null, LOCK_SPACE, b.getOwnerId());
            if (batches.countByOwnerIdAndStatusAndExpiresAtAfter(b.getOwnerId(), ImportBatch.STATUS_PREVIEW,
                    b.getCreatedAt()) >= props.getMaxPendingBatches()) {
                throw BankImportService.pendingLimit(props.getMaxPendingBatches());
            }

            try {
                // 1. Lưu batch trước để có batch.getId() (gán preview để thỏa mãn ràng buộc chk_ib_preview)
                b.setPreview(JSON.writeValueAsString(preview));
                ImportBatch savedBatch = batches.saveAndFlush(b);

                boolean hasImages = imagesByKey != null && !imagesByKey.isEmpty() && questionImageRepo != null;
                if (!hasImages) {
                    return new PreviewSaveResult(savedBatch, preview);
                }

                // 2. Chèn các QuestionImage và ánh xạ token
                List<ImportItem> updatedItems = new ArrayList<>();
                for (ImportItem item : preview.items()) {
                    List<NormalizedImageRef> normImages = imagesByKey != null ? imagesByKey.get(item.key()) : null;
                    if (normImages == null || normImages.isEmpty()) {
                        updatedItems.add(item);
                        continue;
                    }

                    List<QuestionImageDto> imageDtos = new ArrayList<>();
                    String updatedStem = item.stem();
                    List<ItemOption> updatedOptions = item.options() != null ? new ArrayList<>(item.options()) : null;
                    List<ItemStatement> updatedStatements = item.statements() != null ? new ArrayList<>(item.statements()) : null;
                    String updatedExplanation = item.explanation();

                    for (NormalizedImageRef norm : normImages) {
                        QuestionImage qi = new QuestionImage(
                                savedBatch.getOwnerId(), savedBatch.getId(), item.key(), null,
                                norm.role(), norm.ordinal(), norm.mime(), norm.data(), norm.byteSize(),
                                norm.widthPx(), norm.heightPx(), norm.displayWidthMm(), norm.sha256(),
                                norm.sourceFormat()
                        );
                        qi = questionImageRepo.save(qi);

                        String oldToken = "⟦" + norm.tempId() + "⟧";
                        String newToken = "⟦img:" + qi.getId() + "⟧";

                        if (updatedStem != null) {
                            updatedStem = updatedStem.replace(oldToken, newToken);
                        }
                        if (updatedOptions != null) {
                            for (int k = 0; k < updatedOptions.size(); k++) {
                                ItemOption opt = updatedOptions.get(k);
                                if (opt.text() != null && opt.text().contains(oldToken)) {
                                    updatedOptions.set(k, new ItemOption(opt.key(), opt.text().replace(oldToken, newToken)));
                                }
                            }
                        }
                        if (updatedStatements != null) {
                            for (int k = 0; k < updatedStatements.size(); k++) {
                                ItemStatement st = updatedStatements.get(k);
                                if (st.text() != null && st.text().contains(oldToken)) {
                                    updatedStatements.set(k, new ItemStatement(st.key(), st.text().replace(oldToken, newToken), st.label()));
                                }
                            }
                        }
                        if (updatedExplanation != null) {
                            updatedExplanation = updatedExplanation.replace(oldToken, newToken);
                        }

                        imageDtos.add(new QuestionImageDto(
                                qi.getId(), qi.getRole(), qi.getOrdinal(), qi.getMime(),
                                qi.getWidthPx(), qi.getHeightPx(), qi.getDisplayWidthMm(), null
                        ));
                    }

                    updatedItems.add(new ImportItem(
                            item.key(), item.type(), item.locator(), item.levelRaw(), item.level(),
                            updatedStem, updatedOptions != null ? List.copyOf(updatedOptions) : null,
                            item.correctOption(), updatedStatements != null ? List.copyOf(updatedStatements) : null,
                            item.saAnswer(), updatedExplanation, item.flags(), item.blocking(),
                            item.blockReasons(), List.copyOf(imageDtos)
                    ));
                }

                questionImageRepo.flush();

                // 3. Cập nhật preview JSON trong batch
                PreviewData finalPreview = new PreviewData(List.copyOf(updatedItems), preview.skipped());
                savedBatch.setPreview(JSON.writeValueAsString(finalPreview));
                savedBatch = batches.saveAndFlush(savedBatch);

                return new PreviewSaveResult(savedBatch, finalPreview);
            } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException e) {
                ApiErrorException safe = ImportDbErrors.translate(e, "lưu", "mới");
                if (safe == null) {
                    throw e;
                }
                throw safe;
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("Lỗi khi lưu bản xem trước", e);
            }
        }
    }

    @Transactional
    public ImportBatch savePreview(ImportBatch b) {
        return savePreview(b, new PreviewData(List.of(), List.of()), Map.of()).batch();
    }
}
