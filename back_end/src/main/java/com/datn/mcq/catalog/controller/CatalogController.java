package com.datn.mcq.catalog.controller;

import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Danh mục dùng cho sản phẩm. Template chỉ phục vụ công cụ thí nghiệm nên nằm ở {@link CatalogTemplateController}
 * (bật bằng app.experiment.enabled).
 */
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
}
