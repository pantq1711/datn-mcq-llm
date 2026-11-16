package com.datn.mcq.material.repository;

import com.datn.mcq.material.entity.LessonMaterial;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * V31: tài liệu đã xóa mềm ({@code deleted_at} khác null) bị bỏ ở các truy vấn theo chủ sở hữu (xem, danh sách, trần, gắn
 * vào đề). {@link #findByOwnerIdAndSha256} vẫn thấy tài liệu đã xóa để tải lại đúng file thì khôi phục bản cũ (UNIQUE
 * owner_id, sha256). Truy vấn theo id không kèm chủ sở hữu (đọc văn bản cho job của đề) vẫn thấy.
 */
public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, Integer> {

    /** Tài liệu chưa xóa của chính chủ sở hữu; của người khác hoặc đã xóa thì rỗng (nơi gọi trả 404). */
    Optional<LessonMaterial> findByIdAndOwnerIdAndDeletedAtIsNull(Integer id, Integer ownerId);

    /** Tải lại cùng một file (cùng sha256) thì dùng bản cũ của chính chủ sở hữu, kể cả bản đã xóa mềm (để khôi phục). */
    Optional<LessonMaterial> findByOwnerIdAndSha256(Integer ownerId, String sha256);

    /** Tài liệu chưa xóa của chủ sở hữu, mới nhất trước. */
    List<LessonMaterial> findByOwnerIdAndDeletedAtIsNullOrderByIdDesc(Integer ownerId, Pageable pageable);

    /** Số tài liệu chưa xóa của chủ sở hữu (danh sách, trần mỗi giáo viên). */
    long countByOwnerIdAndDeletedAtIsNull(Integer ownerId);

    /**
     * V31: khóa ghi hàng tài liệu chưa xóa của chủ sở hữu (SELECT ... FOR UPDATE) tới hết transaction và trả id; rỗng khi
     * không có, của người khác hoặc đã xóa. Dùng khi xóa: lần tạo đề đang gắn tài liệu này (giữ FOR SHARE, xem
     * {@link #lockOwnedIds}) xong trước rồi mới đếm đề, nên không xóa mềm nhầm tài liệu vừa được gắn vào đề mới.
     */
    @Query(value = "SELECT id FROM lesson_material WHERE id = :id AND owner_id = :ownerId AND deleted_at IS NULL"
            + " FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockOwnedForUpdate(@Param("id") Integer id, @Param("ownerId") Integer ownerId);

    /**
     * Id các tài liệu chưa xóa của {@code ownerId} trong {@code ids}, kèm khóa chia sẻ (FOR SHARE) tới hết transaction: gắn
     * tài liệu vào đề trong lúc có người xóa thì lần xóa phải chờ (rồi bị chặn, không thành lỗi 500). Gọi trong transaction
     * của nơi tạo đề.
     */
    @Query(value = "SELECT id FROM lesson_material WHERE id IN (:ids) AND owner_id = :ownerId AND deleted_at IS NULL"
            + " FOR SHARE", nativeQuery = true)
    List<Integer> lockOwnedIds(@Param("ids") Collection<Integer> ids, @Param("ownerId") Integer ownerId);
}
