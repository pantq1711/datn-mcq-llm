package com.datn.mcq.review.controller;

import com.datn.mcq.review.dto.BlindQuestionReviewDto;
import com.datn.mcq.review.dto.ReviewQuestionListItemDto;
import com.datn.mcq.review.dto.SubmitReviewRequest;
import com.datn.mcq.review.dto.SubmitReviewResponse;
import com.datn.mcq.review.service.MathDemoSeedService;
import com.datn.mcq.review.service.TeacherReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class TeacherReviewController {

    private final TeacherReviewService teacherReviewService;
    private final MathDemoSeedService mathDemoSeedService;

    public TeacherReviewController(
            TeacherReviewService teacherReviewService,
            MathDemoSeedService mathDemoSeedService) {
        this.teacherReviewService = teacherReviewService;
        this.mathDemoSeedService = mathDemoSeedService;
    }

    /**
     * Danh sách chấm mù. Không có tham số lọc theo trạng thái: lọc theo status sẽ cho người chấm suy ra
     * kết quả QC (quyết định 21/10 mục 2). Tham số lạ như ?status= bị bỏ qua.
     */
    @GetMapping("/questions")
    public List<ReviewQuestionListItemDto> listQuestions() {
        return teacherReviewService.listQuestionsForReview();
    }

    @GetMapping("/questions/{id}")
    public BlindQuestionReviewDto getQuestionForBlindReview(@PathVariable Integer id) {
        return teacherReviewService.getBlindQuestionForReview(id);
    }

    @PostMapping("/questions/{id}")
    public SubmitReviewResponse submitReview(
            @PathVariable Integer id,
            @RequestBody SubmitReviewRequest request) {
        return teacherReviewService.submitReview(id, request);
    }

    @PostMapping("/demo/seed-math")
    public List<ReviewQuestionListItemDto> seedMathDemo() {
        return mathDemoSeedService.seedMathDemoQuestions();
    }
}

