package org.example.cipherws.note.admin.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志与 AI 调用统计的数据访问接口。SQL 在 resources/mapper/AdminLogMapper.xml。
 *
 * <p><b>为什么要分成 admin / user 两组方法，而不是传一个 type 进 XML 拼表名：</b>
 * 表名只能靠 {@code ${}} 拼，那是 SQL 注入的口子（哪怕值来自枚举，也开了先例）。
 * 这里把两张表写成四条完整语句（各一对 count/list），Service 只按 type 选方法调用，
 * 任何用户输入都到不了表名位置。</p>
 *
 * <p>日志列表带 JOIN 出来的 {@code actor_name}，统计是聚合，全部 {@code resultType="map"}。
 * ⚠️ Map 的 key 是原始列名（下划线）。</p>
 */
public interface AdminLogMapper {

    // ------------------------------------------------------------------ 管理员操作日志

    /**
     * @param likePattern   已经转义好的 LIKE 模式；null 表示不按关键词过滤
     * @param actorFallback LIKE 兜底词，必须与 SELECT 里的 COALESCE 一致：
     *                      管理员日志用「管理员」，用户日志用「用户」。
     *                      否则「孤儿日志」显示的是兜底词却搜不到这个词。
     * @param fromTime      {@code created_at >= fromTime}；null 表示不设下界
     * @param toTime        {@code created_at <  toTime}（Service 传「结束日期的次日 0 点」）；null 表示不设上界
     */
    Long countAdminLogs(@Param("likePattern") String likePattern,
                        @Param("actorFallback") String actorFallback,
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime);

    List<Map<String, Object>> listAdminLogs(@Param("likePattern") String likePattern,
                                            @Param("actorFallback") String actorFallback,
                                            @Param("fromTime") LocalDateTime fromTime,
                                            @Param("toTime") LocalDateTime toTime,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    // ------------------------------------------------------------------ 用户操作日志

    Long countUserLogs(@Param("likePattern") String likePattern,
                       @Param("actorFallback") String actorFallback,
                       @Param("fromTime") LocalDateTime fromTime,
                       @Param("toTime") LocalDateTime toTime);

    List<Map<String, Object>> listUserLogs(@Param("likePattern") String likePattern,
                                           @Param("actorFallback") String actorFallback,
                                           @Param("fromTime") LocalDateTime fromTime,
                                           @Param("toTime") LocalDateTime toTime,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    /** 审计日志统一从这里落库（详情与 IP 的长度夹取在 Service 里做） */
    int insertAdminLog(@Param("adminId") Long adminId,
                       @Param("type") String type,
                       @Param("detail") String detail,
                       @Param("ip") String ip);

    // ------------------------------------------------------------------ AI 调用统计

    /**
     * 按天分组的调用次数（近 7 天）。
     *
     * @param fromTime 统计起点（含），Service 传「今天 - 6 天」的 0 点
     * @return Map 的 key：{@code day}（java.sql.Date）、{@code calls}（Long）
     */
    List<Map<String, Object>> countAiCallsByDay(@Param("fromTime") LocalDateTime fromTime);

    /**
     * 调用次数排行前 5。
     *
     * @return Map 的 key：{@code user_id}、{@code name}、{@code calls}
     */
    List<Map<String, Object>> topUsers();

    /**
     * 四项总量，用子查询合成一行（省三次往返）。
     *
     * @return Map 的 key：{@code users}、{@code notes}、{@code ai_calls}、{@code flagged_notes}
     */
    Map<String, Object> totals();
}
