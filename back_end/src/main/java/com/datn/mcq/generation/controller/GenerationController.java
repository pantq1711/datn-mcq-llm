package com.datn.mcq.generation.controller;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/generation")
public class GenerationController {

    public record EnqueueJobsRequest(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            Integer count
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
    public List<GenerationJob> enqueueJobs(@RequestBody EnqueueJobsRequest request) {
        int count = request.count() != null ? request.count() : 1;
        return generationService.enqueueJobs(
                request.templateId(),
                request.strategy(),
                request.targetDifficulty(),
                request.modelName(),
                count
        );
    }

    @PostMapping("/jobs/process-next")
    public GenerationJobPoller.ProcessedJobSummary processNextJob(
            @RequestParam(defaultValue = "true") boolean runAnswerCheck) {
        return generationJobPoller.processNextJob(runAnswerCheck);
    }

    @GetMapping("/jobs")
    public List<GenerationJob> listJobs() {
        return generationService.listJobs();
    }
}

