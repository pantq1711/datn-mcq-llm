package com.datn.mcq.catalog.controller;

import com.datn.mcq.catalog.dto.CreateTemplateRequest;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.service.CatalogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Template câu hỏi: chỉ để lấy hoặc tạo {@code templateId} cho công cụ thí nghiệm (/api/generation/*). Ma trận tự tạo
 * template cho từng ô. Chỉ tồn tại khi {@code app.experiment.enabled=true} (quyết định 25/10).
 */
@RestController
@RequestMapping("/api/catalog/templates")
@ConditionalOnProperty(name = "app.experiment.enabled", havingValue = "true")
public class CatalogTemplateController {

    private final CatalogService catalogService;

    public CatalogTemplateController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<QuestionTemplate> listTemplates(@RequestParam(required = false) Integer subjectId) {
        return catalogService.listTemplates(subjectId);
    }

    /** Tạo question_template cho subject đã tồn tại; subject không tồn tại thì 404. */
    @PostMapping
    public QuestionTemplate createTemplate(@RequestBody CreateTemplateRequest request) {
        return catalogService.createTemplate(request);
    }
}
