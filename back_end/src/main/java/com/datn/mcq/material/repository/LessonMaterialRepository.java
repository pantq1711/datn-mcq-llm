package com.datn.mcq.material.repository;

import com.datn.mcq.material.entity.LessonMaterial;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, Integer> {

    /** Tài liệu của chính chủ sở hữu; của người khác thì rỗng (nơi gọi trả 404). */
    Optional<LessonMaterial> findByIdAndOwnerId(Integer id, Integer ownerId);

    /** Tải lại cùng một file (cùng sha256) thì dùng bản cũ của chính chủ sở hữu. */
    Optional<LessonMaterial> findByOwnerIdAndSha256(Integer ownerId, String sha256);

    /** Tài liệu của chủ sở hữu, mới nhất trước. */
    List<LessonMaterial> findByOwnerIdOrderByIdDesc(Integer ownerId, Pageable pageable);

    long countByOwnerId(Integer ownerId);

    /**
     * Id các tài liệu của {@code ownerId} trong {@code ids}, kèm khóa chia sẻ (FOR SHARE) tới hết transaction: gắn tài
     * liệu vào đề trong lúc có người xóa thì lần xóa phải chờ (rồi bị chặn, không thành lỗi 500). Gọi trong transaction
     * của nơi tạo đề.
     */
    @Query(value = "SELECT id FROM lesson_material WHERE id IN (:ids) AND owner_id = :ownerId FOR SHARE",
            nativeQuery = true)
    List<Integer> lockOwnedIds(@Param("ids") Collection<Integer> ids, @Param("ownerId") Integer ownerId);
}
