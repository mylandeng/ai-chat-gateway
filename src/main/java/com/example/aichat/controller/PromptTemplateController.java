package com.example.aichat.controller;

import com.example.aichat.model.dto.PromptTemplateRequest;
import com.example.aichat.model.entity.PromptTemplate;
import com.example.aichat.model.entity.PromptTemplateVersion;
import com.example.aichat.service.PromptTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class PromptTemplateController {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateController.class);

    private final PromptTemplateService templateService;

    public PromptTemplateController(PromptTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public PromptTemplate create(@RequestBody PromptTemplateRequest request,
                                  HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute("tenantId");
        return templateService.create(tenantId, request);
    }

    @GetMapping
    public List<PromptTemplate> list(HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute("tenantId");
        return templateService.listByTenant(tenantId);
    }

    @GetMapping("/{id}")
    public PromptTemplate get(@PathVariable Long id) {
        return templateService.getById(id);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(@PathVariable Long id, @RequestBody PromptTemplateRequest request) {
        return templateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }

    @PostMapping("/{id}/render")
    public Map<String, String> render(@PathVariable Long id,
                                       @RequestBody Map<String, String> variables) {
        String rendered = templateService.render(id, variables);
        return Map.of("rendered", rendered);
    }

    @GetMapping("/{id}/versions")
    public List<PromptTemplateVersion> versions(@PathVariable Long id) {
        return templateService.getVersionHistory(id);
    }

    // === 模板市场（公开，不需要认证） ===

    @GetMapping("/market")
    public Page<PromptTemplate> market(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return templateService.listMarket(category, keyword, page, size);
    }

    // === 收藏 ===

    @PostMapping("/{id}/favorite")
    public void favorite(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute("tenantId");
        templateService.addFavorite(tenantId, id);
    }

    @DeleteMapping("/{id}/favorite")
    public void unfavorite(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute("tenantId");
        templateService.removeFavorite(tenantId, id);
    }

    @GetMapping("/favorites")
    public List<PromptTemplate> favorites(HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute("tenantId");
        return templateService.getFavorites(tenantId);
    }
}
