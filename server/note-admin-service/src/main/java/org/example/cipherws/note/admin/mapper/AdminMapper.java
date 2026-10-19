package org.example.cipherws.note.admin.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiConfig;
import org.example.cipherws.note.common.entity.NoteSystemAnnouncement;
import org.example.cipherws.note.common.entity.NoteUser;

import java.util.List;
import java.util.Map;

/**
 * 后台「主数据」的数据访问接口：用户、笔记巡检、AI 配置、系统公告。
 * SQL 全部在 resources/mapper/AdminMapper.xml 里。
 *
 * <p>两张日志表与统计接口在 {@link AdminLogMapper}：日志查询要按 type 切表，
 * 单独放一个接口才能做到「两个方法、两条完整语句」，不用 ${} 拼表名。</p>
 *
 * <p><b>为什么有一堆 Map 返回值：</b>这些查询要么带聚合列（{@code note_count}），
 * 要么带 JOIN 出来的展示名（{@code author_name}），都不与 note-common 里的实体一一对应。
 * 硬套实体会多出一堆没人用的字段，所以统一 {@code resultType="map"}。
 * ⚠️ Map 的 key 是**原始列名（下划线）**，{@code map-underscore-to-camel-case} 只作用于实体，
 * 对 Map 不生效；另外 MyBatis 默认不写入 null 列，取值一律走 Service 里的空值兜底工具。</p>
 */
public interface AdminMapper {

    // ------------------------------------------------------------------ 用户管理

    /**
     * 用户总数。
     *
     * @param likePattern 已经转义好的 LIKE 模式（{@code %kw%}）；<b>null 表示不按关键词过滤</b>。
     *                    为什么在 Java 侧拼模式：关键词里的 % 和 _ 必须被转义成普通字符，
     *                    放到 SQL 里写 CONCAT('%', #{kw}, '%') 会丢掉转义语义。
     */
    Long countUsers(@Param("likePattern") String likePattern);

    /** 用户列表：带 {@code note_count}（未删除笔记数）子查询，故用 Map */
    List<Map<String, Object>> listUsers(@Param("likePattern") String likePattern,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    /** 单条用户视图，列与 {@link #listUsers} 完全一致，故共用同一段 SQL 片段 */
    Map<String, Object> findUserVo(@Param("id") long id);

    /** 写审计日志前取「改之前」的样子：只要账号、启用状态、额度三列 */
    NoteUser findUserBrief(@Param("id") long id);

    int updateUserEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    int updateUserQuota(@Param("id") long id, @Param("aiQuota") int aiQuota);

    // ------------------------------------------------------------------ 笔记巡检

    Long countInspectNotes(@Param("likePattern") String likePattern);

    /** 巡检列表：带 JOIN 出来的 {@code author_name}，故用 Map */
    List<Map<String, Object>> listInspectNotes(@Param("likePattern") String likePattern,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    Map<String, Object> findInspectNote(@Param("id") long id);

    /** 只取标题做日志文案；查不到返回 null 由 Service 翻成「笔记不存在」 */
    Note findNoteBrief(@Param("id") long id);

    /**
     * 需求 4.6.2 的硬约束：管理员只能标记违规，这条 UPDATE 里只有 {@code flagged} 一列。
     * 全模块唯一允许写 note 表的语句，不要在这里加 title / content。
     */
    int updateNoteFlagged(@Param("id") long id, @Param("flagged") boolean flagged);

    /** 一页笔记的标签一次查完（逐个 note 查会退化成 N+1）。空集合不要调用。 */
    List<Map<String, Object>> findTagsByNoteIds(@Param("noteIds") List<Long> noteIds);

    // ------------------------------------------------------------------ AI 配置

    /** 单行配置表，恒定 {@code id = 1}；没有这一行时返回 null（Service 翻成初始化提示） */
    NoteAiConfig findAiConfig();

    /** apiKey 为空时的更新：不碰 {@code api_key} 列，等于沿用旧密钥 */
    int updateAiConfigKeepKey(@Param("endpoint") String endpoint,
                              @Param("model") String model,
                              @Param("maxChars") int maxChars,
                              @Param("adminId") Long adminId);

    int updateAiConfigWithKey(@Param("endpoint") String endpoint,
                              @Param("model") String model,
                              @Param("maxChars") int maxChars,
                              @Param("apiKey") String apiKey,
                              @Param("adminId") Long adminId);

    // ------------------------------------------------------------------ 系统公告

    Long countAnnouncements();

    List<Map<String, Object>> listAnnouncements(@Param("limit") int limit, @Param("offset") int offset);

    NoteSystemAnnouncement findAnnouncement(@Param("id") long id);

    /** 普通用户可见的公告：只取 enabled=1，数量天然很少，不分页 */
    List<Map<String, Object>> listPublishedAnnouncements();

    /** 自增主键会回填到入参实体的 id 上（XML 里配了 useGeneratedKeys） */
    int insertAnnouncement(NoteSystemAnnouncement announcement);

    int updateAnnouncement(@Param("id") long id,
                           @Param("title") String title,
                           @Param("content") String content,
                           @Param("enabled") boolean enabled);

    int deleteAnnouncement(@Param("id") long id);
}
