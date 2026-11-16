package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * V31 (NV-01): đề đã xóa ({@code deleted_at} khác null) bị bỏ ở mọi truy vấn theo người dùng và mọi phép đếm "đang dùng",
 * trừ {@link #countByMatrixId} (sửa cấu trúc ma trận vẫn bị khóa khi ma trận đã từng có đề).
 */
public interface ExamRepository extends JpaRepository<Exam, Integer> {
    boolean existsByExamCode(String examCode);

    /** W1-1: đề chỉ khi thuộc người gọi (không thì coi như không tồn tại). V31: đề đã xóa cũng như không tồn tại. */
    Optional<Exam> findByIdAndCreatedByAndDeletedAtIsNull(Integer id, Integer createdBy);

    /**
     * V31: khóa ghi hàng đề chưa xóa của người gọi (SELECT ... FOR UPDATE) tới hết transaction và trả id; rỗng khi không có,
     * của người khác hoặc đã xóa. Dùng khi xóa đề: hai lần xóa đồng thời thì lần sau chờ rồi thấy đề đã xóa (404).
     */
    @Query(value = "SELECT id FROM exam WHERE id = :id AND created_by = :owner AND deleted_at IS NULL FOR UPDATE",
            nativeQuery = true)
    Optional<Integer> lockLiveForUpdate(@Param("id") Integer id, @Param("owner") Integer owner);

    /**
     * V31: khóa CHIA SẺ (FOR SHARE) hàng đề chưa xóa tới hết transaction và trả id; rỗng khi không có hoặc đã xóa. Dùng ở đầu
     * các thao tác có thể ghi chỗ hoặc xếp job (Giữ, Đổi/Soạn lại, Sửa, và đối soát khi có chỗ đang sinh); chủ sở hữu kiểm
     * riêng. Xung đột với khóa của lần xóa đề ({@link #lockLiveForUpdate}): thao tác đang chạy thì lần xóa chờ rồi mới đếm
     * job; lần xóa đang dở thì thao tác chờ, PostgreSQL kiểm lại {@code deleted_at IS NULL} trên bản ghi mới sau khi lần xóa
     * commit nên trả rỗng (404). Các thao tác với nhau không chờ nhau (khóa chia sẻ).
     */
    @Query(value = "SELECT id FROM exam WHERE id = :id AND deleted_at IS NULL FOR SHARE", nativeQuery = true)
    Optional<Integer> lockLiveForShare(@Param("id") Integer id);

    /** W1-1: đề chưa xóa của một người (GET /api/exams), thứ tự theo {@code pageable}. */
    Page<Exam> findByCreatedByAndDeletedAtIsNull(Integer createdBy, Pageable pageable);

    /**
     * W1-4: số đề đã TỪNG tạo từ một ma trận, KỂ CẢ đề đã xóa (V31): sửa cấu trúc ma trận đã từng có đề bị khóa, vì chỗ của
     * đề đã xóa vẫn trỏ tới ô.
     */
    long countByMatrixId(Integer matrixId);

    /** V31: số đề CHƯA XÓA tạo từ một ma trận (xóa ma trận, examCount hiển thị). */
    long countByMatrixIdAndDeletedAtIsNull(Integer matrixId);

    /** W1-4: số đề chưa xóa theo từng ma trận: các dòng {@code [matrixId, số đề]}; ma trận không có đề thì không có dòng. */
    @Query("SELECT e.matrixId, COUNT(e) FROM Exam e WHERE e.matrixId IN :matrixIds AND e.deletedAt IS NULL"
            + " GROUP BY e.matrixId")
    List<Object[]> countByMatrixIds(@Param("matrixIds") Collection<Integer> matrixIds);

    /**
     * W1-4: đề chưa xóa của một người theo loại ma trận gốc ('matrix' hoặc 'lesson'); đề không có ma trận không thuộc loại
     * nào.
     */
    @Query("SELECT e FROM Exam e WHERE e.createdBy = :createdBy AND e.deletedAt IS NULL AND EXISTS "
            + "(SELECT 1 FROM ExamMatrix m WHERE m.id = e.matrixId AND m.kind = :kind)")
    Page<Exam> findByCreatedByAndMatrixKind(@Param("createdBy") Integer createdBy, @Param("kind") String kind,
                                            Pageable pageable);
}
