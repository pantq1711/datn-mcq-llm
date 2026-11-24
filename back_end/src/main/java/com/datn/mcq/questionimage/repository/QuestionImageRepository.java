package com.datn.mcq.questionimage.repository;

import com.datn.mcq.questionimage.model.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface QuestionImageRepository extends JpaRepository<QuestionImage, Long> {

    /**
     * Projection chỉ đọc metadata ảnh, KHÔNG nạp cột bytea data để tiết kiệm bộ nhớ khi dựng DTO.
     */
    interface MetadataProjection {
        Long getId();
        Integer getOwnerId();
        Integer getImportBatchId();
        String getItemKey();
        Integer getGeneratedQuestionId();
        String getRole();
        Short getOrdinal();
        String getMime();
        Integer getByteSize();
        Integer getWidthPx();
        Integer getHeightPx();
        Short getDisplayWidthMm();
        String getSha256();
        String getSourceFormat();
    }

    @Query("SELECT q.id AS id, q.ownerId AS ownerId, q.importBatchId AS importBatchId, q.itemKey AS itemKey, " +
           "q.generatedQuestionId AS generatedQuestionId, q.role AS role, q.ordinal AS ordinal, q.mime AS mime, " +
           "q.byteSize AS byteSize, q.widthPx AS widthPx, q.heightPx AS heightPx, q.displayWidthMm AS displayWidthMm, " +
           "q.sha256 AS sha256, q.sourceFormat AS sourceFormat " +
           "FROM QuestionImage q WHERE q.importBatchId = :batchId ORDER BY q.itemKey ASC, q.ordinal ASC")
    List<MetadataProjection> findMetadataByImportBatchId(@Param("batchId") Integer batchId);

    @Query("SELECT q.id AS id, q.ownerId AS ownerId, q.importBatchId AS importBatchId, q.itemKey AS itemKey, " +
           "q.generatedQuestionId AS generatedQuestionId, q.role AS role, q.ordinal AS ordinal, q.mime AS mime, " +
           "q.byteSize AS byteSize, q.widthPx AS widthPx, q.heightPx AS heightPx, q.displayWidthMm AS displayWidthMm, " +
           "q.sha256 AS sha256, q.sourceFormat AS sourceFormat " +
           "FROM QuestionImage q WHERE q.generatedQuestionId IN :questionIds ORDER BY q.generatedQuestionId ASC, q.ordinal ASC")
    List<MetadataProjection> findMetadataByGeneratedQuestionIdIn(@Param("questionIds") Collection<Integer> questionIds);

    /** Dung lượng (byte đã lưu) của từng ảnh, để kiểm trần trước khi nạp byte (O2, xuất Word); không nạp cột data. */
    interface SizeProjection {
        Long getId();
        Integer getByteSize();
    }

    @Query("SELECT q.id AS id, q.byteSize AS byteSize FROM QuestionImage q WHERE q.id IN :ids")
    List<SizeProjection> findSizesByIdIn(@Param("ids") Collection<Long> ids);

    /** Byte ảnh để nhúng vào file Word (O2): chỉ các cột cần cho data URI. */
    interface ExportProjection {
        Long getId();
        String getMime();
        byte[] getData();
        Short getDisplayWidthMm();
    }

    @Query("SELECT q.id AS id, q.mime AS mime, q.data AS data, q.displayWidthMm AS displayWidthMm "
           + "FROM QuestionImage q WHERE q.id IN :ids")
    List<ExportProjection> findExportDataByIdIn(@Param("ids") Collection<Long> ids);

    /** Id (trong {@code questionIds}) của các câu nguồn có ít nhất một ảnh, mọi role; không nạp cột data. */
    @Query("SELECT DISTINCT q.generatedQuestionId FROM QuestionImage q WHERE q.generatedQuestionId IN :questionIds")
    List<Integer> findGeneratedQuestionIdsWithImages(@Param("questionIds") Collection<Integer> questionIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM QuestionImage q WHERE q.importBatchId = :batchId AND q.itemKey NOT IN :keptKeys")
    int deleteUnselectedImages(@Param("batchId") Integer batchId, @Param("keptKeys") Collection<String> keptKeys);

    @Transactional
    @Modifying
    @Query("UPDATE QuestionImage q SET q.generatedQuestionId = :gqId WHERE q.importBatchId = :batchId AND q.itemKey = :itemKey")
    int assignGeneratedQuestionId(@Param("batchId") Integer batchId, @Param("itemKey") String itemKey, @Param("gqId") Integer gqId);
}
