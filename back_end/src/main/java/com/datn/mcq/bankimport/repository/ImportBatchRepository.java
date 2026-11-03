package com.datn.mcq.bankimport.repository;

import com.datn.mcq.bankimport.entity.ImportBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Integer> {

    Optional<ImportBatch> findByIdAndOwnerId(Integer id, Integer ownerId);

    /** Khóa lô khi xác nhận, để hai lần xác nhận đồng thời không cùng ghi ngân hàng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ImportBatch b WHERE b.id = :id AND b.ownerId = :ownerId")
    Optional<ImportBatch> lockByIdAndOwnerId(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

    /** Số lô đang chờ xác nhận và chưa hết hạn của một giáo viên. */
    long countByOwnerIdAndStatusAndExpiresAtAfter(Integer ownerId, String status, LocalDateTime now);

    /** Lô chờ quá hạn thành expired và bỏ nội dung bản xem trước; trả số lô đã đổi. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE import_batch SET status = 'expired', preview = NULL"
            + " WHERE status = 'preview' AND expires_at <= :now", nativeQuery = true)
    int expirePreviews(@Param("now") LocalDateTime now);
}
