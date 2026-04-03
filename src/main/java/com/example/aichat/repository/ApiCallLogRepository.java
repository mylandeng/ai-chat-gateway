package com.example.aichat.repository;

import com.example.aichat.model.entity.ApiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {

    @Query("SELECT new map(" +
           "CAST(l.createdAt AS date) as date, " +
           "l.model as model, " +
           "COUNT(l) as callCount, " +
           "COALESCE(SUM(l.totalTokens), 0) as totalTokens, " +
           "COALESCE(SUM(l.promptTokens), 0) as promptTokens, " +
           "COALESCE(SUM(l.completionTokens), 0) as completionTokens, " +
           "COALESCE(AVG(l.durationMs), 0) as avgDuration) " +
           "FROM ApiCallLog l " +
           "WHERE l.tenantId = :tenantId " +
           "AND l.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(l.createdAt AS date), l.model " +
           "ORDER BY CAST(l.createdAt AS date)")
    List<Map<String, Object>> statsByTenantAndDateRange(
        @Param("tenantId") Long tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
           "l.keyId as keyId, " +
           "COUNT(l) as callCount, " +
           "COALESCE(SUM(l.totalTokens), 0) as totalTokens) " +
           "FROM ApiCallLog l " +
           "WHERE l.tenantId = :tenantId " +
           "AND l.createdAt BETWEEN :start AND :end " +
           "GROUP BY l.keyId")
    List<Map<String, Object>> statsByKey(
        @Param("tenantId") Long tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
           "l.model as model, " +
           "COUNT(l) as callCount, " +
           "COALESCE(SUM(l.totalTokens), 0) as totalTokens, " +
           "COALESCE(AVG(l.durationMs), 0) as avgDuration) " +
           "FROM ApiCallLog l " +
           "WHERE l.tenantId = :tenantId " +
           "AND l.createdAt BETWEEN :start AND :end " +
           "GROUP BY l.model")
    List<Map<String, Object>> statsByModel(
        @Param("tenantId") Long tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
           "COUNT(l) as callCount, " +
           "COALESCE(SUM(l.totalTokens), 0) as totalTokens, " +
           "SUM(CASE WHEN l.status = 'error' THEN 1 ELSE 0 END) as errorCount, " +
           "COALESCE(AVG(l.durationMs), 0) as avgDuration) " +
           "FROM ApiCallLog l " +
           "WHERE l.tenantId = :tenantId " +
           "AND l.createdAt >= :today")
    Map<String, Object> todaySummary(
        @Param("tenantId") Long tenantId,
        @Param("today") LocalDateTime today);

    /**
     * 查询租户在指定日期范围内的总 token 消耗
     */
    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM ApiCallLog l " +
           "WHERE l.tenantId = :tenantId AND l.createdAt BETWEEN :start AND :end")
    Long sumTokensByTenantAndDateRange(
        @Param("tenantId") Long tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
