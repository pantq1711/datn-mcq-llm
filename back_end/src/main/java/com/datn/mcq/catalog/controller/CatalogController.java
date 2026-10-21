package com.datn.mcq.catalog.controller;

import com.datn.mcq.catalog.dto.CreateTemplateRequest;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/subjects")
    public List<Subject> listSubjects() {
        return catalogService.listSubjects();
    }

    @GetMapping("/templates")
    public List<QuestionTemplate> listTemplates(@RequestParam(required = false) Integer subjectId) {
        return catalogService.listTemplates(subjectId);
    }

    /** Tạo question_template cho subject đã tồn tại; subject không tồn tại thì 404. */
    @PostMapping("/templates")
    public QuestionTemplate createTemplate(@RequestBody CreateTemplateRequest request) {
        return catalogService.createTemplate(request);
    }
}

