package org.example.cipherws.note.admin.dto;

import java.util.List;

/**
 * 后台服务的全部出参 VO 与入参 DTO。
 *
 * <p>为什么都塞在一个文件里：这些 record 只服务于 note-admin-service，字段又都很短，
 * 拆成二十个文件反而更难找。嵌套 record 隐式是 static，Jackson 也能直接按组件名
 * 序列化 / 反序列化，不需要额外注解。</p>
 *
 * <p>统一约定：出参 VO 用基本类型并对空值兜底，前端不用判空；入参 DTO 用包装类型，
 * 因为「字段缺省」和「字段给 null」在编辑类接口里含义不同（缺省 = 这一项不改）。</p>
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ------------------------------------------------------------------ 用户管理

    /**
     * 后台用户列表项。
     *
     * <p>刻意没有 password 字段：需求 6.2.1 要求密码不可逆存储，
     * 顺手把摘要回传出去等于给离线爆破留了口子。</p>
     */
    public record UserVo(
            Long id,
            String username,
            String nickname,
            String email,
            String role,
            boolean enabled,
            Integer aiQuota,
            Long noteCount,
            String createdAt) {
    }

    /** 启用 / 禁用请求体 */
    public record UserStatusRequest(Boolean enabled) {
    }

    /** 重置 AI 额度请求体 */
    public record UserQuotaRequest(Integer aiQuota) {
    }

    // ------------------------------------------------------------------ 笔记巡检

    /** 巡检列表项，带作者名与标签；正文只读展示，后台不提供修改入口 */
    public record InspectNoteVo(
            Long id,
            String title,
            String content,
            String status,
            boolean flagged,
            String authorName,
            String updatedAt,
            List<TagVo> tags) {
    }

    /** 笔记标签（前端只用到 id 与 name） */
    public record TagVo(Long id, String name) {
    }

    /** 标记 / 取消标记违规 */
    public record NoteFlagRequest(Boolean flagged) {
    }

    // ------------------------------------------------------------------ AI 配置

    /**
     * AI 配置出参。
     *
     * <p>只有 {@code apiKeyMasked}，没有 apiKey：需求 6.2.4 明确密钥不在前端暴露，
     * 明文一旦回传，XSS 或截图就能直接拿走。</p>
     */
    public record AiConfigVo(
            String endpoint,
            String model,
            Integer maxChars,
            String apiKeyMasked,
            String updatedAt) {
    }

    /** AI 配置入参：apiKey 为空 / null 表示沿用旧密钥 */
    public record AiConfigRequest(
            String endpoint,
            String apiKey,
            String model,
            Integer maxChars) {
    }

    // ------------------------------------------------------------------ 公告

    /** 公告 VO，管理员与普通用户共用一份结构 */
    public record AnnouncementVo(
            Long id,
            String title,
            String content,
            boolean enabled,
            String createdAt) {
    }

    /** 公告入参：编辑时字段可空，空表示这一项不改 */
    public record AnnouncementRequest(
            String title,
            String content,
            Boolean enabled) {
    }

    // ------------------------------------------------------------------ 日志与统计

    /** 操作日志项：type 是操作类型（登录 / 禁用用户 …），不是 user/admin */
    public record LogVo(
            Long id,
            String type,
            String detail,
            String actorName,
            String ip,
            String createdAt) {
    }

    /** 近 7 天里某一天的 AI 调用次数 */
    public record DailyCountVo(String date, long count) {
    }

    /** AI 调用次数排行的一项 */
    public record TopUserVo(Long userId, String name, long count) {
    }

    /** 统计页顶部的四个数字 */
    public record TotalsVo(long users, long notes, long aiCalls, long flaggedNotes) {
    }

    /** 统计接口的整体返回结构，字段名与前端 admin/stats 页面严格一致 */
    public record StatsVo(List<DailyCountVo> daily, List<TopUserVo> topUsers, TotalsVo totals) {
    }
}
