package com.example.aichat.rag.service;

import com.example.aichat.rag.model.KnowledgeBase;
import com.example.aichat.rag.repository.KnowledgeBaseRepository;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.repository.RagChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeDocumentRepository docRepo;
    private final RagChatSessionRepository sessionRepo;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                KnowledgeDocumentRepository docRepo,
                                RagChatSessionRepository sessionRepo) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.sessionRepo = sessionRepo;
    }

    public KnowledgeBase create(Long tenantId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(tenantId);
        kb.setName(name);
        kb.setDescription(description);
        log.info("[知识库] 创建: name={}, tenantId={}", name, tenantId);
        return kbRepo.save(kb);
    }

    public List<KnowledgeBase> listByTenant(Long tenantId) {
        return kbRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public KnowledgeBase getByIdAndTenant(Long id, Long tenantId) {
        return kbRepo.findById(id)
                .filter(kb -> kb.getTenantId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
    }

    public KnowledgeBase update(Long id, Long tenantId, String name, String description) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        log.info("[知识库] 更新: id={}, name={}", id, kb.getName());
        return kbRepo.save(kb);
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        sessionRepo.deleteByKbId(id);
        docRepo.deleteByKbId(id);
        kbRepo.delete(kb);
        log.info("[知识库] 删除: id={}, name={}", id, kb.getName());
    }

    public void refreshDocCount(Long kbId) {
        int count = docRepo.countByKbId(kbId);
        kbRepo.findById(kbId).ifPresent(kb -> {
            kb.setDocCount(count);
            kbRepo.save(kb);
        });
    }

    /**
     * 开启/关闭分享
     */
    public KnowledgeBase toggleShare(Long id, Long tenantId, boolean enabled) {
        KnowledgeBase kb = getByIdAndTenant(id, tenantId);
        if (enabled) {
            kb.setVisibility("shared");
            if (kb.getShareToken() == null) {
                kb.setShareToken(UUID.randomUUID().toString().replace("-", ""));
            }
        } else {
            kb.setVisibility("private");
        }
        log.info("[知识库] 分享设置: id={}, enabled={}", id, enabled);
        return kbRepo.save(kb);
    }
}
