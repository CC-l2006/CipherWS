package org.example.cipherws.note.admin.service;

import org.example.cipherws.note.admin.dto.AdminDtos.AiConfigRequest;
import org.example.cipherws.note.admin.dto.AdminDtos.AnnouncementRequest;
import org.example.cipherws.note.common.result.Result;

/**
 * 后台业务接口。
 *
 * <p>所有 {@code /admin/**} 接口的管理员身份校验放在 controller 第一行（{@code Auth.requireAdmin}），
 * 这里只负责业务与审计：每个会改动数据的方法都要求传 {@code adminId}，
 * 用于写 {@code admin_operation_log}（需求 4.7.3：管理员操作可追溯）。</p>
 */
public interface AdminService {

    /** 用户列表：账号 / 昵称 / 邮箱模糊搜索，按 ID 升序，分页 */
    Result listUsers(String keyword, Integer page, Integer size);

    /** 启用 / 禁用账号；不能禁用自己 */
    Result toggleUserStatus(Long adminId, Long targetUserId, Boolean enabled);

    /** 重置 AI 额度，取值 0-9999 */
    Result resetQuota(Long adminId, Long targetUserId, Integer aiQuota);

    /** 笔记巡检列表：标题或作者模糊搜索，按更新时间倒序，分页 */
    Result inspectNotes(String keyword, Integer page, Integer size);

    /** 标记 / 取消标记违规；需求 4.6.2 只允许改 flagged，不改正文 */
    Result flagNote(Long adminId, Long noteId, Boolean flagged);

    /** 读取 AI 配置（密钥只回掩码） */
    Result getAiConfig();

    /** 保存 AI 配置；apiKey 为空表示沿用旧密钥 */
    Result saveAiConfig(Long adminId, AiConfigRequest request);

    /** 后台公告列表，分页 */
    Result listAnnouncements(Integer page, Integer size);

    /** 发布公告 */
    Result createAnnouncement(Long adminId, AnnouncementRequest request);

    /** 编辑公告，字段为空表示不改 */
    Result updateAnnouncement(Long adminId, Long id, AnnouncementRequest request);

    /** 删除公告 */
    Result removeAnnouncement(Long adminId, Long id);

    /** 普通用户可见的公告：只要 enabled=1，按发布时间倒序 */
    Result listPublishedAnnouncements();

    /** 操作日志查询：type 选 user / admin，支持关键词与日期范围 */
    Result listLogs(String type, String keyword, String from, String to, Integer page, Integer size);

    /** 统计页：近 7 天调用趋势、调用排行、总量 */
    Result stats();
}
