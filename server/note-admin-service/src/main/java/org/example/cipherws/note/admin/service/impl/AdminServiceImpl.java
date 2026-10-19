package org.example.cipherws.note.admin.service.impl;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.cipherws.note.admin.dto.AdminDtos.AiConfigRequest;
import org.example.cipherws.note.admin.dto.AdminDtos.AiConfigVo;
import org.example.cipherws.note.admin.dto.AdminDtos.AnnouncementRequest;
import org.example.cipherws.note.admin.dto.AdminDtos.AnnouncementVo;
import org.example.cipherws.note.admin.dto.AdminDtos.DailyCountVo;
import org.example.cipherws.note.admin.dto.AdminDtos.InspectNoteVo;
import org.example.cipherws.note.admin.dto.AdminDtos.LogVo;
import org.example.cipherws.note.admin.dto.AdminDtos.StatsVo;
import org.example.cipherws.note.admin.dto.AdminDtos.TagVo;
import org.example.cipherws.note.admin.dto.AdminDtos.TopUserVo;
import org.example.cipherws.note.admin.dto.AdminDtos.TotalsVo;
import org.example.cipherws.note.admin.dto.AdminDtos.UserVo;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台业务实现。全部走 JdbcTemplate + 预编译占位符：
 * 表名固定写在常量里（`note_user` / `note` / `note_tag` 与系统库同名，一律加反引号），
 * 只有值走 {@code ?}，从不拼接用户输入。
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    /**
     * 时间统一输出成 {@code yyyy-MM-dd'T'HH:mm:ss}。
     * 为什么不用 MySQL 默认的「空格分隔」：前端多处 {@code new Date(value)} 直接解析，
     * 空格分隔的字符串在部分浏览器会得到 Invalid Date，带 T 的写法各浏览器都认。
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 单页上限：防止前端传 size=100000 把整张表读进内存 */
    private static final int MAX_PAGE_SIZE = 100;
    /** detail 列是 VARCHAR(255)，超长会被数据库直接拒绝 */
    private static final int DETAIL_MAX = 255;
    /** ip 列是 VARCHAR(64)，X-Forwarded-For 可能带一长串代理链 */
    private static final int IP_MAX = 64;

    private static final String SQL_USER_SELECT =
            "SELECT u.`id`, u.`username`, u.`nickname`, u.`email`, u.`role`, u.`enabled`, u.`ai_quota`,"
                    + " (SELECT COUNT(*) FROM `note` n WHERE n.`user_id` = u.`id` AND n.`deleted` = 0) AS `note_count`,"
                    + " u.`created_at`";
    private static final String SQL_USER_FROM = " FROM `note_user` u";

    private static final String SQL_NOTE_INSPECT_SELECT =
            "SELECT n.`id`, n.`title`, n.`content`, n.`status`, n.`flagged`, n.`updated_at`,"
                    + " COALESCE(NULLIF(u.`nickname`, ''), u.`username`, '未知用户') AS `author_name`";
    /**
     * 用 LEFT JOIN 而不是 JOIN：作者注销后笔记还在回收站 / 巡检视角里，
     * 内连接会让这些行整行消失，管理员就查不到违规内容了。
     */
    private static final String SQL_NOTE_INSPECT_FROM =
            " FROM `note` n LEFT JOIN `note_user` u ON u.`id` = n.`user_id`";

    private static final String SQL_AI_CONFIG_SELECT =
            "SELECT `endpoint`, `api_key`, `model`, `max_chars`, `updated_at` FROM `note_ai_config` WHERE `id` = 1";

    private static final String SQL_ANNOUNCEMENT_SELECT =
            "SELECT `id`, `title`, `content`, `enabled`, `created_at` FROM `note_system_announcement`";

    private static final String SQL_ADMIN_LOG_SELECT =
            "SELECT l.`id`, l.`type`, l.`detail`, l.`ip`, l.`created_at`,"
                    + " COALESCE(NULLIF(u.`nickname`, ''), u.`username`, '管理员') AS `actor_name`";
    private static final String SQL_ADMIN_LOG_FROM =
            " FROM `note_admin_operation_log` l LEFT JOIN `note_user` u ON u.`id` = l.`admin_id`";

    private static final String SQL_USER_LOG_SELECT =
            "SELECT l.`id`, l.`type`, l.`detail`, l.`ip`, l.`created_at`,"
                    + " COALESCE(NULLIF(u.`nickname`, ''), u.`username`, '用户') AS `actor_name`";
    private static final String SQL_USER_LOG_FROM =
            " FROM `note_user_operation_log` l LEFT JOIN `note_user` u ON u.`id` = l.`user_id`";

    private static final RowMapper<UserVo> USER_VO_MAPPER = (rs, rowNum) -> new UserVo(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getBoolean("enabled"),
            rs.getInt("ai_quota"),
            rs.getLong("note_count"),
            formatTime(rs.getTimestamp("created_at")));

    private static final RowMapper<InspectNoteVo> INSPECT_NOTE_MAPPER = (rs, rowNum) -> new InspectNoteVo(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("status"),
            rs.getBoolean("flagged"),
            rs.getString("author_name"),
            formatTime(rs.getTimestamp("updated_at")),
            List.of());

    private static final RowMapper<AnnouncementVo> ANNOUNCEMENT_MAPPER = (rs, rowNum) -> new AnnouncementVo(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getBoolean("enabled"),
            formatTime(rs.getTimestamp("created_at")));

    private static final RowMapper<LogVo> LOG_MAPPER = (rs, rowNum) -> new LogVo(
            rs.getLong("id"),
            rs.getString("type"),
            rs.getString("detail"),
            rs.getString("actor_name"),
            rs.getString("ip"),
            formatTime(rs.getTimestamp("created_at")));

    @Resource
    private JdbcTemplate jdbcTemplate;

    // ================================================================== 用户管理

    @Override
    public Result listUsers(String keyword, Integer page, Integer size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        String kw = clean(keyword);
        if (!kw.isEmpty()) {
            where.append(" AND (u.`username` LIKE ? OR u.`nickname` LIKE ? OR u.`email` LIKE ?)");
            String pattern = like(kw);
            Collections.addAll(args, pattern, pattern, pattern);
        }
        // 为什么单独 COUNT：LIMIT 之后拿不到总条数，前端分页器要靠 total 算页数
        long total = count("SELECT COUNT(*)" + SQL_USER_FROM + where, args);
        List<Object> pageArgs = withPaging(args, page, size);
        List<UserVo> rows = jdbcTemplate.query(
                SQL_USER_SELECT + SQL_USER_FROM + where + " ORDER BY u.`id` ASC LIMIT ? OFFSET ?",
                USER_VO_MAPPER, pageArgs.toArray());
        return Result.ok(rows, total);
    }

    /**
     * 变更与审计日志放在同一个事务里：要么两件事都成功，要么都没发生，
     * 免得出现「后台操作了但日志里查不到」这种对不上账的情况。
     */
    @Override
    @Transactional
    public Result toggleUserStatus(Long adminId, Long targetUserId, Boolean enabled) {
        if (targetUserId == null) {
            throw new BusinessException("用户不存在");
        }
        boolean next = Boolean.TRUE.equals(enabled);
        if (targetUserId.equals(adminId) && !next) {
            // 自己把自己禁掉就再也进不来后台了；「启用自己」是无害空操作，放行
            throw new BusinessException("不能禁用自己");
        }
        UserBrief before = findUserBrief(targetUserId);
        jdbcTemplate.update("UPDATE `note_user` SET `enabled` = ? WHERE `id` = ?", next, targetUserId);
        writeLog(adminId, next ? "启用用户" : "禁用用户",
                "账号 " + before.username() + "，状态 " + stateText(before.enabled())
                        + " -> " + stateText(next));
        return Result.ok(findUserVo(targetUserId));
    }

    @Override
    @Transactional
    public Result resetQuota(Long adminId, Long targetUserId, Integer aiQuota) {
        if (aiQuota == null || aiQuota < 0 || aiQuota > 9999) {
            throw new BusinessException("AI 额度需为 0-9999 的整数");
        }
        if (targetUserId == null) {
            throw new BusinessException("用户不存在");
        }
        UserBrief before = findUserBrief(targetUserId);
        jdbcTemplate.update("UPDATE `note_user` SET `ai_quota` = ? WHERE `id` = ?", aiQuota, targetUserId);
        writeLog(adminId, "重置 AI 配额",
                "账号 " + before.username() + "，额度 " + before.aiQuota() + " -> " + aiQuota);
        return Result.ok(findUserVo(targetUserId));
    }

    // ================================================================== 笔记巡检

    @Override
    public Result inspectNotes(String keyword, Integer page, Integer size) {
        // 回收站里的笔记用户自己都看不到了，巡检只看线上内容
        StringBuilder where = new StringBuilder(" WHERE n.`deleted` = 0");
        List<Object> args = new ArrayList<>();
        String kw = clean(keyword);
        if (!kw.isEmpty()) {
            // 列表里展示的是昵称，所以昵称也得能搜到，否则用户看到「池禾」却搜不出来
            where.append(" AND (n.`title` LIKE ? OR u.`username` LIKE ? OR u.`nickname` LIKE ?)");
            String pattern = like(kw);
            Collections.addAll(args, pattern, pattern, pattern);
        }
        long total = count("SELECT COUNT(*)" + SQL_NOTE_INSPECT_FROM + where, args);
        List<Object> pageArgs = withPaging(args, page, size);
        List<InspectNoteVo> rows = jdbcTemplate.query(
                SQL_NOTE_INSPECT_SELECT + SQL_NOTE_INSPECT_FROM + where
                        + " ORDER BY n.`updated_at` DESC, n.`id` DESC LIMIT ? OFFSET ?",
                INSPECT_NOTE_MAPPER, pageArgs.toArray());
        return Result.ok(withTags(rows), total);
    }

    /**
     * 需求 4.6.2 / 7.3 硬约束：管理员只能标记违规，绝不能改用户笔记的标题和正文。
     * 所以这条 UPDATE 里只有 flagged 一列，改正文的口子在这里被彻底焊死。
     */
    @Override
    @Transactional
    public Result flagNote(Long adminId, Long noteId, Boolean flagged) {
        if (noteId == null) {
            throw new BusinessException("笔记不存在");
        }
        NoteBrief before = findNoteBrief(noteId);
        boolean next = Boolean.TRUE.equals(flagged);
        jdbcTemplate.update("UPDATE `note` SET `flagged` = ? WHERE `id` = ?", next, noteId);
        // 日志类型与建表脚本注释里的约定保持一致
        writeLog(adminId, "巡检笔记",
                (next ? "标记违规" : "取消标记") + "《" + before.title() + "》");
        return Result.ok(findInspectNote(noteId));
    }

    // ================================================================== AI 配置

    @Override
    public Result getAiConfig() {
        return Result.ok(readAiConfig());
    }

    @Override
    @Transactional
    public Result saveAiConfig(Long adminId, AiConfigRequest request) {
        if (request == null) {
            throw new BusinessException("请填写 AI 配置");
        }
        AiConfigVo before = readAiConfig();
        String endpoint = clean(request.endpoint());
        String model = clean(request.model());
        Integer maxChars = request.maxChars();

        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new BusinessException("接口地址需以 http:// 或 https:// 开头");
        }
        if (endpoint.length() > 255) {
            throw new BusinessException("接口地址不能超过 255 个字符");
        }
        if (model.isEmpty()) {
            throw new BusinessException("模型名称不能为空");
        }
        if (model.length() > 64) {
            throw new BusinessException("模型名称不能超过 64 个字符");
        }
        if (maxChars == null || maxChars < 100 || maxChars > 20000) {
            throw new BusinessException("最大字数需为 100-20000 的整数");
        }

        String apiKey = clean(request.apiKey());
        if (apiKey.isEmpty()) {
            // 前端读到的永远是掩码，用户不改密钥时根本不会提交这个字段，
            // 所以「空」必须解释成沿用旧密钥，而不是把密钥清空
            jdbcTemplate.update(
                    "UPDATE `note_ai_config` SET `endpoint` = ?, `model` = ?, `max_chars` = ?, `updated_by` = ?"
                            + " WHERE `id` = 1",
                    endpoint, model, maxChars, adminId);
        } else {
            if (apiKey.length() > 255) {
                throw new BusinessException("接口密钥不能超过 255 个字符");
            }
            jdbcTemplate.update(
                    "UPDATE `note_ai_config` SET `endpoint` = ?, `model` = ?, `max_chars` = ?, `api_key` = ?,"
                            + " `updated_by` = ? WHERE `id` = 1",
                    endpoint, model, maxChars, apiKey, adminId);
        }

        writeLog(adminId, "修改 AI 配置",
                "模型 " + before.model() + " -> " + model
                        + "，最大字数 " + before.maxChars() + " -> " + maxChars
                        + (apiKey.isEmpty() ? "，密钥未修改" : "，密钥已更新"));
        return Result.ok(true);
    }

    // ================================================================== 后台公告

    @Override
    public Result listAnnouncements(Integer page, Integer size) {
        long total = count("SELECT COUNT(*) FROM `note_system_announcement`", List.of());
        List<Object> pageArgs = withPaging(List.of(), page, size);
        List<AnnouncementVo> rows = jdbcTemplate.query(
                SQL_ANNOUNCEMENT_SELECT + " ORDER BY `created_at` DESC, `id` DESC LIMIT ? OFFSET ?",
                ANNOUNCEMENT_MAPPER, pageArgs.toArray());
        return Result.ok(rows, total);
    }

    @Override
    @Transactional
    public Result createAnnouncement(Long adminId, AnnouncementRequest request) {
        if (adminId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        String title = requireTitle(request == null ? null : request.title());
        String content = requireContent(request == null ? null : request.content());
        // 前端不传 enabled 时按「立即启用」处理，和历史行为一致
        boolean enabled = request == null || !Boolean.FALSE.equals(request.enabled());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `note_system_announcement` (`title`, `content`, `admin_id`, `enabled`)"
                            + " VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setLong(3, adminId);
            ps.setBoolean(4, enabled);
            return ps;
        }, keyHolder);
        // 自增主键由驱动回填，不能靠再查一次 LAST_INSERT_ID()：连接池里可能是另一条连接
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new BusinessException("公告发布失败，请稍后重试");
        }
        writeLog(adminId, "发布公告", "发布《" + title + "》");
        return Result.ok(findAnnouncement(generated.longValue()));
    }

    @Override
    @Transactional
    public Result updateAnnouncement(Long adminId, Long id, AnnouncementRequest request) {
        if (id == null) {
            throw new BusinessException("公告不存在");
        }
        AnnouncementVo before = findAnnouncement(id);
        if (before == null) {
            throw new BusinessException("公告不存在");
        }
        // 字段为 null = 这一项不改；给了值就必须先过校验，避免把空标题写进库里
        String title = request == null || request.title() == null ? before.title() : requireTitle(request.title());
        String content = request == null || request.content() == null
                ? before.content() : requireContent(request.content());
        boolean enabled = (request == null || request.enabled() == null)
                ? before.enabled() : request.enabled().booleanValue();

        jdbcTemplate.update(
                "UPDATE `note_system_announcement` SET `title` = ?, `content` = ?, `enabled` = ? WHERE `id` = ?",
                title, content, enabled, id);
        writeLog(adminId, "编辑公告",
                "编辑《" + title + "》" + (before.enabled() == enabled ? "" : (enabled ? "，已启用" : "，已下线")));
        return Result.ok(findAnnouncement(id));
    }

    @Override
    @Transactional
    public Result removeAnnouncement(Long adminId, Long id) {
        if (id == null) {
            throw new BusinessException("公告不存在");
        }
        AnnouncementVo before = findAnnouncement(id);
        if (before == null) {
            throw new BusinessException("公告不存在");
        }
        jdbcTemplate.update("DELETE FROM `note_system_announcement` WHERE `id` = ?", id);
        writeLog(adminId, "删除公告", "删除《" + before.title() + "》");
        return Result.ok(true);
    }

    // ================================================================== 普通用户公告

    @Override
    public Result listPublishedAnnouncements() {
        // 公告数量天然很少，一次全给前端，不必为了统一而强行分页
        List<AnnouncementVo> rows = jdbcTemplate.query(
                SQL_ANNOUNCEMENT_SELECT + " WHERE `enabled` = 1 ORDER BY `created_at` DESC, `id` DESC",
                ANNOUNCEMENT_MAPPER);
        return Result.ok(rows, (long) rows.size());
    }

    // ============================================================== 日志与统计

    @Override
    public Result listLogs(String type, String keyword, String from, String to,
                           Integer page, Integer size) {
        boolean adminLog = "admin".equalsIgnoreCase(clean(type));
        String select = adminLog ? SQL_ADMIN_LOG_SELECT : SQL_USER_LOG_SELECT;
        String fromClause = adminLog ? SQL_ADMIN_LOG_FROM : SQL_USER_LOG_FROM;
        String actorFallback = adminLog ? "管理员" : "用户";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        String kw = clean(keyword);
        if (!kw.isEmpty()) {
            // 操作人是 JOIN 出来的，LIKE 里的兜底值必须和 SELECT 的 COALESCE 一致，
            // 否则「孤儿日志」显示的是「管理员」却搜不到这个词
            where.append(" AND (l.`type` LIKE ? OR l.`detail` LIKE ?"
                    + " OR COALESCE(NULLIF(u.`nickname`, ''), u.`username`, ?) LIKE ?)");
            String pattern = like(kw);
            Collections.addAll(args, pattern, pattern, actorFallback, pattern);
        }
        if (!clean(from).isEmpty()) {
            where.append(" AND l.`created_at` >= ?");
            args.add(Timestamp.valueOf(parseDay(from, "起始日期").atStartOfDay()));
        }
        if (!clean(to).isEmpty()) {
            // 结束日期含当天整天：用「次日 0 点之前」比较，比 <= 23:59:59 更稳，
            // created_at 带毫秒也不会漏掉最后一条
            where.append(" AND l.`created_at` < ?");
            args.add(Timestamp.valueOf(parseDay(to, "结束日期").plusDays(1).atStartOfDay()));
        }

        long total = count("SELECT COUNT(*)" + fromClause + where, args);
        List<Object> pageArgs = withPaging(args, page, size);
        List<LogVo> rows = jdbcTemplate.query(
                select + fromClause + where + " ORDER BY l.`created_at` DESC, l.`id` DESC LIMIT ? OFFSET ?",
                LOG_MAPPER, pageArgs.toArray());
        return Result.ok(rows, total);
    }

    @Override
    public Result stats() {
        LocalDate start = LocalDate.now().minusDays(6);
        List<Map<String, Object>> grouped = jdbcTemplate.queryForList(
                "SELECT DATE(`created_at`) AS `day`, COUNT(*) AS `calls` FROM `note_ai_summary_record`"
                        + " WHERE `created_at` >= ? GROUP BY DATE(`created_at`)",
                Timestamp.valueOf(start.atStartOfDay()));
        Map<String, Long> byDay = new HashMap<>();
        for (Map<String, Object> row : grouped) {
            Object day = row.get("day");
            String key = day instanceof java.sql.Date date
                    ? date.toLocalDate().format(DAY_FORMAT) : String.valueOf(day);
            byDay.put(key, ((Number) row.get("calls")).longValue());
        }

        // 没有调用的日子也要占一格并给 0：前端按固定 7 根柱子画趋势图，缺日期会错位
        List<DailyCountVo> daily = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            String key = start.plusDays(i).format(DAY_FORMAT);
            daily.add(new DailyCountVo(key, byDay.getOrDefault(key, 0L)));
        }

        // GROUP BY 带上昵称与账号，既满足 ONLY_FULL_GROUP_BY，也能直接取到展示名
        List<TopUserVo> topUsers = jdbcTemplate.query(
                "SELECT r.`user_id` AS `user_id`,"
                        + " COALESCE(NULLIF(u.`nickname`, ''), u.`username`, '未知用户') AS `name`,"
                        + " COUNT(*) AS `calls`"
                        + " FROM `note_ai_summary_record` r LEFT JOIN `note_user` u ON u.`id` = r.`user_id`"
                        + " GROUP BY r.`user_id`, u.`nickname`, u.`username`"
                        + " ORDER BY `calls` DESC, r.`user_id` ASC LIMIT 5",
                (rs, rowNum) -> new TopUserVo(
                        rs.getLong("user_id"), rs.getString("name"), rs.getLong("calls")));

        // 四个总量都是一次性的，用子查询合成一行，省三次往返
        TotalsVo summed = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM `note_user`) AS `users`,"
                        + " (SELECT COUNT(*) FROM `note` WHERE `deleted` = 0) AS `notes`,"
                        + " (SELECT COUNT(*) FROM `note_ai_summary_record`) AS `ai_calls`,"
                        + " (SELECT COUNT(*) FROM `note` WHERE `flagged` = 1 AND `deleted` = 0)"
                        + " AS `flagged_notes`",
                (rs, rowNum) -> new TotalsVo(
                        rs.getLong("users"), rs.getLong("notes"),
                        rs.getLong("ai_calls"), rs.getLong("flagged_notes")));

        return Result.ok(new StatsVo(daily, topUsers, Objects.requireNonNullElse(summed, new TotalsVo(0, 0, 0, 0))));
    }

    // ================================================================ 内部工具

    /** 审计日志的最小视图：只要账号、启用状态和额度 */
    private record UserBrief(String username, boolean enabled, int aiQuota) {
    }

    /** 笔记巡检只需要标题来做日志文案 */
    private record NoteBrief(String title) {
    }

    /** 标签查询的中间行：一个 note 对多个 tag，先摊平再按 note 分组 */
    private record TagRow(Long noteId, TagVo tag) {
    }

    private UserBrief findUserBrief(Long userId) {
        List<UserBrief> rows = jdbcTemplate.query(
                "SELECT `username`, `enabled`, `ai_quota` FROM `note_user` WHERE `id` = ?",
                (rs, rowNum) -> new UserBrief(
                        rs.getString("username"), rs.getBoolean("enabled"), rs.getInt("ai_quota")),
                userId);
        if (rows.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return rows.get(0);
    }

    private UserVo findUserVo(Long userId) {
        List<UserVo> rows = jdbcTemplate.query(
                SQL_USER_SELECT + SQL_USER_FROM + " WHERE u.`id` = ?", USER_VO_MAPPER, userId);
        if (rows.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return rows.get(0);
    }

    private NoteBrief findNoteBrief(Long noteId) {
        List<NoteBrief> rows = jdbcTemplate.query(
                "SELECT `title` FROM `note` WHERE `id` = ?",
                (rs, rowNum) -> new NoteBrief(rs.getString("title")),
                noteId);
        if (rows.isEmpty()) {
            throw new BusinessException("笔记不存在");
        }
        return rows.get(0);
    }

    private InspectNoteVo findInspectNote(Long noteId) {
        List<InspectNoteVo> rows = jdbcTemplate.query(
                SQL_NOTE_INSPECT_SELECT + SQL_NOTE_INSPECT_FROM + " WHERE n.`id` = ?",
                INSPECT_NOTE_MAPPER, noteId);
        if (rows.isEmpty()) {
            throw new BusinessException("笔记不存在");
        }
        InspectNoteVo note = rows.get(0);
        return withTags(note, loadTags(List.of(noteId)).getOrDefault(noteId, List.of()));
    }

    /** 把一页笔记的标签一次补齐：逐行查会退化成 N+1 次往返 */
    private List<InspectNoteVo> withTags(List<InspectNoteVo> notes) {
        if (notes.isEmpty()) {
            return notes;
        }
        List<Long> ids = notes.stream().map(InspectNoteVo::id).toList();
        Map<Long, List<TagVo>> tags = loadTags(ids);
        return notes.stream()
                .map(note -> withTags(note, tags.getOrDefault(note.id(), List.of())))
                .toList();
    }

    private static InspectNoteVo withTags(InspectNoteVo note, List<TagVo> tags) {
        return new InspectNoteVo(note.id(), note.title(), note.content(), note.status(),
                note.flagged(), note.authorName(), note.updatedAt(), tags);
    }

    private Map<Long, List<TagVo>> loadTags(List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        // IN (?, ?, ?) 的占位符个数按页大小生成，仍然只有值走参数，没有拼 SQL 值
        String placeholders = String.join(", ", Collections.nCopies(noteIds.size(), "?"));
        List<TagRow> rows = jdbcTemplate.query(
                "SELECT r.`note_id`, t.`id`, t.`name` FROM `note_tag_rel` r"
                        + " JOIN `note_tag` t ON t.`id` = r.`tag_id`"
                        + " WHERE r.`note_id` IN (" + placeholders + ") ORDER BY t.`name` ASC",
                (rs, rowNum) -> new TagRow(rs.getLong("note_id"),
                        new TagVo(rs.getLong("id"), rs.getString("name"))),
                noteIds.toArray());
        Map<Long, List<TagVo>> grouped = new LinkedHashMap<>();
        for (TagRow row : rows) {
            grouped.computeIfAbsent(row.noteId(), key -> new ArrayList<>()).add(row.tag());
        }
        return grouped;
    }

    private AiConfigVo readAiConfig() {
        List<AiConfigVo> rows = jdbcTemplate.query(SQL_AI_CONFIG_SELECT, (rs, rowNum) -> new AiConfigVo(
                rs.getString("endpoint"),
                rs.getString("model"),
                rs.getInt("max_chars"),
                maskKey(rs.getString("api_key")),
                formatTime(rs.getTimestamp("updated_at"))));
        if (rows.isEmpty()) {
            throw new BusinessException("AI 配置不存在，请先执行 note-schema.sql 初始化");
        }
        return rows.get(0);
    }

    private AnnouncementVo findAnnouncement(Long id) {
        List<AnnouncementVo> rows = jdbcTemplate.query(
                SQL_ANNOUNCEMENT_SELECT + " WHERE `id` = ?", ANNOUNCEMENT_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 需求 6.2.4：密钥不在前端暴露。只回前 6 位加固定长度的星号，
     * 既能看出「配没配、配的是哪一把」，又不足以还原原文。
     */
    private static String maskKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String visible = apiKey.substring(0, Math.min(6, apiKey.length()));
        return visible + "********";
    }

    /**
     * 审计日志统一从这里落库。IP 属于传输层信息，不该塞进每个业务方法签名，
     * 所以直接取当前请求上下文；取不到（异步线程 / 定时任务）就留空，
     * 不会因为记不上 IP 把主流程搞失败。
     */
    private void writeLog(Long adminId, String type, String detail) {
        if (adminId == null) {
            // 正常请求一定有管理员 ID；真缺了只跳过审计，不让业务白做
            log.warn("缺少管理员身份，跳过审计日志：type={}, detail={}", type, detail);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO `note_admin_operation_log` (`admin_id`, `type`, `detail`, `ip`) VALUES (?, ?, ?, ?)",
                adminId, type, clip(detail, DETAIL_MAX), clip(currentIp(), IP_MAX));
    }

    private static String currentIp() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    // 网关会在最前面追加真实客户端，后面的都是各级代理
                    return forwarded.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) {
                    return realIp.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (RuntimeException e) {
            log.debug("取请求 IP 失败，按空值处理", e);
        }
        return "";
    }

    private static String requireTitle(String title) {
        String value = clean(title);
        if (value.isEmpty()) {
            throw new BusinessException("公告标题不能为空");
        }
        if (value.length() > 80) {
            throw new BusinessException("公告标题不能超过 80 个字符");
        }
        return value;
    }

    private static String requireContent(String content) {
        String value = clean(content);
        if (value.isEmpty()) {
            throw new BusinessException("公告内容不能为空");
        }
        return value;
    }

    private long count(String sql, List<Object> args) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    private static List<Object> withPaging(List<Object> args, Integer page, Integer size) {
        List<Object> all = new ArrayList<>(args);
        all.add(pageSize(size));
        all.add(offset(page, size));
        return all;
    }

    private static int pageSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int offset(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        return (safePage - 1) * pageSize(size);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** 关键词里的 % 和 _ 是 LIKE 的通配符，转义后才能当普通字符搜 */
    private static String like(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String formatTime(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(TIME_FORMAT);
    }

    private static String stateText(boolean enabled) {
        return enabled ? "启用" : "禁用";
    }

    private static LocalDate parseDay(String value, String label) {
        try {
            return LocalDate.parse(clean(value), DAY_FORMAT);
        } catch (DateTimeParseException e) {
            // 直接把 SQL 层的解析异常翻成用户看得懂的话，而不是「服务异常」
            throw new BusinessException(label + "格式需为 yyyy-MM-dd");
        }
    }
}
