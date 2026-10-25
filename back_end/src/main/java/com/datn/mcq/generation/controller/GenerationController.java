package com.datn.mcq.generation.controller;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.dto.GenerationJobDto;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Công cụ thí nghiệm: sinh đồng bộ, bơm job, chạy job, xem job. Gọi Gemini nên có thể tốn phí. Chỉ tồn tại khi
 * {@code app.experiment.enabled=true} (mặc định tắt, quyết định 25/10); khi bật vẫn chỉ admin gọi được.
 */
@RestController
@RequestMapping("/api/generation")
@ConditionalOnProperty(name = "app.experiment.enabled", havingValue = "true")
public class GenerationController {

    /**
     * @param questionType                dạng câu (V4); bỏ trống là mcq
     * @param targetStatementDifficulties (tf, bắt buộc) mức mục tiêu của 4 ý a-d
     */
    public record EnqueueJobsRequest(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            Integer count,
            QuestionType questionType,
            List<Difficulty> targetStatementDifficulties
    ) {}

    private final GenerationService generationService;
    private final GenerationJobPoller generationJobPoller;

    public GenerationController(
            GenerationService generationService,
            GenerationJobPoller generationJobPoller) {
        this.generationService = generationService;
        this.generationJobPoller = generationJobPoller;
    }

    @PostMapping("/generate-sync")
    public GenerationService.SyncGenerationResponse generateSync(
            @RequestBody GenerationService.SyncGenerationRequest request) {
        return generationService.generateSync(request);
    }

    @PostMapping("/jobs/enqueue")
    public List<GenerationJobDto> enqueueJobs(@RequestBody EnqueueJobsRequest request) {
        int count = request.count() != null ? request.count() : 1;
        return generationService.enqueueJobs(
                request.templateId(),
                request.strategy(),
                request.targetDifficulty(),
                request.modelName(),
                count,
                request.questionType(),
                request.targetStatementDifficulties()
        ).stream().map(GenerationJobDto::fromEntity).toList();
    }

    @PostMapping("/jobs/process-next")
    public GenerationJobPoller.ProcessedJobSummary processNextJob(
            @RequestParam(defaultValue = "true") boolean runAnswerCheck) {
        return generationJobPoller.processNextJob(runAnswerCheck);
    }

    @GetMapping("/jobs")
    public List<GenerationJobDto> listJobs() {
        return generationService.listJobs().stream().map(GenerationJobDto::fromEntity).toList();
    }
}

