package com.example.aichat.repository;

import com.example.aichat.model.entity.PromptTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    List<PromptTemplate> findByTenantIdAndStatusOrderByUpdatedAtDesc(Long tenantId, Integer status);

    Page<PromptTemplate> findByIsPublicTrueAndStatus(Integer status, Pageable pageable);

    Page<PromptTemplate> findByIsPublicTrueAndStatusAndCategory(Integer status, String category, Pageable pageable);

    @Query("SELECT t FROM PromptTemplate t WHERE t.isPublic = true AND t.status = 1 " +
           "AND (t.name LIKE %:keyword% OR t.description LIKE %:keyword%)")
    Page<PromptTemplate> searchPublic(@Param("keyword") String keyword, Pageable pageable);
}
