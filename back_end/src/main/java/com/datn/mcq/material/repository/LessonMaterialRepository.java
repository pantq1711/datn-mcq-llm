package com.datn.mcq.material.repository;

import com.datn.mcq.material.entity.LessonMaterial;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
