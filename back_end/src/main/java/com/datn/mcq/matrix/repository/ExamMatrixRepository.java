package com.datn.mcq.matrix.repository;

import com.datn.mcq.matrix.entity.ExamMatrix;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamMatrixRepository extends JpaRepository<ExamMatrix, Integer> {
    /** W1-1: ma trận của một người, mới nhất trước. W1-4: chỉ loại {@code kind} (ma trận ẩn của sinh theo bài bị bỏ). */
    List<ExamMatrix> findAllByCreatedByAndKindOrderByIdDesc(Integer createdBy, String kind);

    /** W1-1: ma trận chỉ khi thuộc người gọi (không thì coi như không tồn tại). W1-4: và đúng loại {@code kind}. */
    Optional<ExamMatrix> findByIdAndCreatedByAndKind(Integer id, Integer createdBy, String kind);

    /**
     * W1-4: khóa ghi hàng ma trận của người gọi (SELECT ... FOR UPDATE) tới hết transaction và trả id; rỗng khi không có,
     * của người khác hoặc sai loại. Dùng khi sửa/xóa ma trận: chờ các lần tạo đề đang dở (khóa ngoại của đề giữ FOR KEY
     * SHARE trên hàng) rồi mới đếm đề. Phải là FOR UPDATE thật: {@code @Lock(PESSIMISTIC_WRITE)} của Hibernate trên
     * PostgreSQL là FOR NO KEY UPDATE, không xung đột với FOR KEY SHARE nên không chờ. Đọc entity bằng {@code findById}
     * sau khi có khóa.
     */
    @Query(value = "SELECT m.id FROM exam_matrix m WHERE m.id = :id AND m.created_by = :owner AND m.kind = :kind"
            + " FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockRowForUpdate(@Param("id") Integer id, @Param("owner") Integer owner,
                                       @Param("kind") String kind);

    /** W1-4: như trên nhưng khóa chia sẻ (FOR SHARE); dùng khi tạo đề từ ma trận. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT m FROM ExamMatrix m WHERE m.id = :id AND m.createdBy = :owner AND m.kind = :kind")
    Optional<ExamMatrix> lockOwnedForShare(@Param("id") Integer id, @Param("owner") Integer owner,
                                           @Param("kind") String kind);
}
