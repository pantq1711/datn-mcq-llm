package com.datn.mcq.exam.service;

import com.datn.mcq.exam.repository.ExamGenerationContextRepository;
import com.datn.mcq.material.service.MaterialUsageLookup;
import org.springframework.stereotype.Component;

/** Cài đặt {@link MaterialUsageLookup} của module material: đếm đề có ngữ cảnh dùng tài liệu (exam_context_material). */
@Component
public class ExamMaterialUsage implements MaterialUsageLookup {

    private final ExamGenerationContextRepository repository;

    public ExamMaterialUsage(ExamGenerationContextRepository repository) {
        this.repository = repository;
    }

    @Override
    public int examCount(Integer materialId) {
        return (int) repository.countExamsUsingMaterial(materialId);
    }
}
