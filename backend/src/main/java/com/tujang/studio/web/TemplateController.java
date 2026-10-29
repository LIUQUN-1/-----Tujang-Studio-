package com.tujang.studio.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.service.TemplateService;
import com.tujang.studio.web.dto.CategoryDto;
import com.tujang.studio.web.dto.TemplateDetailDto;
import com.tujang.studio.web.dto.TemplateSummaryDto;

/**
 * 模板与分类的只读接口（docs/api.md §2）。
 */
@RestController
@RequestMapping("/api")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return templateService.categories();
    }

    @GetMapping("/templates")
    public List<TemplateSummaryDto> templates(
            @RequestParam(name = "category", required = false) String category) {
        return templateService.list(category);
    }

    @GetMapping("/templates/{id}")
    public TemplateDetailDto template(@PathVariable String id) {
        return templateService.detail(id);
    }
}
