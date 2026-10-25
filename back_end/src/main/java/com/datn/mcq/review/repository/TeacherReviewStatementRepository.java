package com.datn.mcq.review.repository;

import com.datn.mcq.review.entity.TeacherReviewStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherReviewStatementRepository
        extends JpaRepository<TeacherReviewStatement, TeacherReviewStatement.Key> {
    List<TeacherReviewStatement> findByReviewIdOrderByPositionAsc(Integer reviewId);

    /** Số ý giáo viên chọn khớp nhãn LLM (0-4) của một lần chấm câu tf; chỉ số cấp câu suy ra từ label_correct. */
    long countByReviewIdAndLabelCorrectTrue(Integer reviewId);

    /** Xóa đánh giá từng ý của một lần chấm (trong transaction của nơi gọi), dùng khi duyệt lại production. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TeacherReviewStatement s WHERE s.reviewId = :reviewId")
    int deleteByReviewId(@Param("reviewId") Integer reviewId);
}
