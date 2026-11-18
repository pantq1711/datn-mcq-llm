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

    @Transactional
    @Modifying
    @Query("DELETE FROM QuestionImage q WHERE q.importBatchId = :batchId AND q.itemKey NOT IN :keptKeys")
    int deleteUnselectedImages(@Param("batchId") Integer batchId, @Param("keptKeys") Collection<String> keptKeys);

    @Transactional
    @Modifying
    @Query("UPDATE QuestionImage q SET q.generatedQuestionId = :gqId WHERE q.importBatchId = :batchId AND q.itemKey = :itemKey")
    int assignGeneratedQuestionId(@Param("batchId") Integer batchId, @Param("itemKey") String itemKey, @Param("gqId") Integer gqId);
}
