package org.example.cipherws.note.admin.service.impl;

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
import org.example.cipherws.note.admin.mapper.AdminLogMapper;
import org.example.cipherws.note.admin.mapper.AdminMapper;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiConfig;
import org.example.cipherws.note.common.entity.NoteSystemAnnouncement;
import org.example.cipherws.note.common.entity.NoteUser;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台业务实现。数据访问全部走 {@link AdminMapper} / {@link AdminLogMapper}（MyBatis），
 * 这个文件里没有一行 SQL，只负责业务判断：
 * <ul>
 *   <li>校验（额度范围、公告标题长度、日期格式…）与异常文案；</li>
 *   <li>审计日志：每个改数据的方法都写一条 admin 操作日志，与变更同事务；</li>
 *   <li>分页夹取（默认 10 条、上限 100 条）与结果形态转换（Map/实体 -&gt; 出参 VO）。</li>
 * </ul>
 *
 * <p><b>Map 返回值注意：</b>投影 / 聚合查询用的是 {@code resultType="map"}，key 是**原始列名
 * （下划线）**——{@code map-underscore-to-camel-case} 只对实体生效。所有取值都过一遍
 * {@link #stringVal} / {@link #longVal} / {@link #intVal} / {@link #boolVal} / {@link #formatTime}
 * 这组工具：MyBatis 默认不把 null 列写进 Map，缺 key 就等价于原来的 {@code rs.getXxx} 拿到 null。</p>
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

    /** 日志兜底词：管理员日志里 JOIN 不到人时显示「管理员」 */
    private static final String ACTOR_ADMIN = "管理员";
    /** 日志兜底词：用户日志里 JOIN 不到人时显示「用户」 */
    private static final String ACTOR_USER = "用户";

    private final AdminMapper adminMapper;
    private final AdminLogMapper adminLogMapper;

    public AdminServiceImpl(AdminMapper adminMapper, AdminLogMapper adminLogMapper) {
        this.adminMapper = adminMapper;
        this.adminLogMapper = adminLogMapper;
    }

    // ================================================================== 用户管理

    @Override
    public Result listUsers(String keyword, Integer page, Integer size) {
        String pattern = likePattern(keyword);
        // 为什么单独 COUNT：LIMIT 之后拿不到总条数，前端分页器要靠 total 算页数
        long total = count(adminMapper.countUsers(pattern));
        List<UserVo> rows = adminMapper.listUsers(pattern, pageSize(size), offset(page, size))
                .stream()
                .map(AdminServiceImpl::toUserVo)
                .toList();
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
        adminMapper.updateUserEnabled(targetUserId, next);
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
        adminMapper.updateUserQuota(targetUserId, aiQuota);
        writeLog(adminId, "重置 AI 配额",
                "账号 " + before.username() + "，额度 " + before.aiQuota() + " -> " + aiQuota);
        return Result.ok(findUserVo(targetUserId));
    }

    // ================================================================== 笔记巡检

    @Override
    public Result inspectNotes(String keyword, Integer page, Integer size) {
        // 回收站里的笔记用户自己都看不到了，巡检只看线上内容（deleted = 0 写在 SQL 片段里）
        String pattern = likePattern(keyword);
        long total = count(adminMapper.countInspectNotes(pattern));
        List<InspectNoteVo> rows = adminMapper.listInspectNotes(pattern, pageSize(size), offset(page, size))
                .stream()
                .map(AdminServiceImpl::toInspectNoteVo)
                .toList();
        return Result.ok(withTags(rows), total);
    }

    /**
     * 需求 4.6.2 / 7.3 硬约束：管理员只能标记违规，绝不能改用户笔记的标题和正文。
     * 所以对应的 UPDATE 里只有 flagged 一列，改正文的口子在这里被彻底焊死。
     */
    @Override
    @Transactional
    public Result flagNote(Long adminId, Long noteId, Boolean flagged) {
        if (noteId == null) {
            throw new BusinessException("笔记不存在");
        }
        Note before = findNoteBrief(noteId);
        boolean next = Boolean.TRUE.equals(flagged);
        adminMapper.updateNoteFlagged(noteId, next);
        // 日志类型与建表脚本注释里的约定保持一致
        writeLog(adminId, "巡检笔记",
                (next ? "标记违规" : "取消标记") + "《" + before.getTitle() + "》");
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
            // 所以「空」必须解释成沿用旧密钥，而不是把密钥清空 —— 对应不碰 api_key 列的 UPDATE
            adminMapper.updateAiConfigKeepKey(endpoint, model, maxChars, adminId);
        } else {
            if (apiKey.length() > 255) {
                throw new BusinessException("接口密钥不能超过 255 个字符");
            }
            adminMapper.updateAiConfigWithKey(endpoint, model, maxChars, apiKey, adminId);
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
        long total = count(adminMapper.countAnnouncements());
        List<AnnouncementVo> rows = adminMapper.listAnnouncements(pageSize(size), offset(page, size))
                .stream()
                .map(AdminServiceImpl::toAnnouncementVo)
                .toList();
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

        NoteSystemAnnouncement entity = new NoteSystemAnnouncement();
        entity.setTitle(title);
        entity.setContent(content);
        entity.setAdminId(adminId);
        entity.setEnabled(enabled);
        // 自增主键由驱动回填到实体（useGeneratedKeys），不用再查一次 LAST_INSERT_ID()：
        // 连接池里那次查询可能落到另一条连接上
        adminMapper.insertAnnouncement(entity);
        Long generated = entity.getId();
        if (generated == null) {
            throw new BusinessException("公告发布失败，请稍后重试");
        }
        writeLog(adminId, "发布公告", "发布《" + title + "》");
        return Result.ok(findAnnouncement(generated));
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

        adminMapper.updateAnnouncement(id, title, content, enabled);
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
        adminMapper.deleteAnnouncement(id);
        writeLog(adminId, "删除公告", "删除《" + before.title() + "》");
        return Result.ok(true);
    }

    // ================================================================== 普通用户公告

    @Override
    public Result listPublishedAnnouncements() {
        // 公告数量天然很少，一次全给前端，不必为了统一而强行分页
        List<AnnouncementVo> rows = adminMapper.listPublishedAnnouncements()
                .stream()
                .map(AdminServiceImpl::toAnnouncementVo)
                .toList();
        return Result.ok(rows, (long) rows.size());
    }

    // ============================================================== 日志与统计

    @Override
    public Result listLogs(String type, String keyword, String from, String to,
                           Integer page, Integer size) {
        // type 决定查哪张表。表名在 XML 里是写死的两条完整语句，Service 只按类型选方法调用，
        // 没有任何字符串拼进去 —— 这是不用 ${} 拼表名换来的安全边界。
        boolean adminLog = "admin".equalsIgnoreCase(clean(type));
        String pattern = likePattern(keyword);
        // 操作人是 JOIN 出来的，LIKE 里的兜底词必须和 SELECT 的 COALESCE 一致，
        // 否则「孤儿日志」显示的是「管理员」却搜不到这个词
        String actorFallback = adminLog ? ACTOR_ADMIN : ACTOR_USER;
        // 日期解析放在查询之前：格式错误直接抛「起始日期/结束日期格式需为 yyyy-MM-dd」，不白跑一次 SQL
        LocalDateTime fromTime = clean(from).isEmpty() ? null : parseDay(from, "起始日期").atStartOfDay();
        LocalDateTime toTime = clean(to).isEmpty() ? null : parseDay(to, "结束日期").plusDays(1).atStartOfDay();
        int limit = pageSize(size);
        int offset = offset(page, size);

        long total;
        List<Map<String, Object>> rows;
        if (adminLog) {
            total = count(adminLogMapper.countAdminLogs(pattern, actorFallback, fromTime, toTime));
            rows = adminLogMapper.listAdminLogs(pattern, actorFallback, fromTime, toTime, limit, offset);
        } else {
            total = count(adminLogMapper.countUserLogs(pattern, actorFallback, fromTime, toTime));
            rows = adminLogMapper.listUserLogs(pattern, actorFallback, fromTime, toTime, limit, offset);
        }
        List<LogVo> logs = rows.stream().map(AdminServiceImpl::toLogVo).toList();
        return Result.ok(logs, total);
    }

    @Override
    public Result stats() {
        LocalDate start = LocalDate.now().minusDays(6);
        // key 是 day / calls（下划线原样：Map 不参与 map-underscore-to-camel-case）
        List<Map<String, Object>> grouped = adminLogMapper.countAiCallsByDay(start.atStartOfDay());
        Map<String, Long> byDay = new HashMap<>();
        for (Map<String, Object> row : grouped) {
            Object day = row.get("day");
            String key = day instanceof java.sql.Date date
                    ? date.toLocalDate().format(DAY_FORMAT) : String.valueOf(day);
            byDay.put(key, longVal(row.get("calls")));
        }

        // 没有调用的日子也要占一格并给 0：前端按固定 7 根柱子画趋势图，缺日期会错位
        List<DailyCountVo> daily = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            String key = start.plusDays(i).format(DAY_FORMAT);
            daily.add(new DailyCountVo(key, byDay.getOrDefault(key, 0L)));
        }

        // key 是 user_id / name / calls
        List<TopUserVo> topUsers = adminLogMapper.topUsers().stream()
                .map(row -> new TopUserVo(
                        longVal(row.get("user_id")), stringVal(row.get("name")), longVal(row.get("calls"))))
                .toList();

        // key 是 users / notes / ai_calls / flagged_notes；聚合查询恒返回一行，兜 null 只是防御
        Map<String, Object> totals = Objects.requireNonNullElse(adminLogMapper.totals(), Map.of());
        TotalsVo summed = new TotalsVo(
                longVal(totals.get("users")),
                longVal(totals.get("notes")),
                longVal(totals.get("ai_calls")),
                longVal(totals.get("flagged_notes")));

        return Result.ok(new StatsVo(daily, topUsers, summed));
    }

    // ================================================================ 内部工具

    /** 审计日志的最小视图：只要账号、启用状态和额度 */
    private record UserBrief(String username, boolean enabled, int aiQuota) {
    }

    private UserBrief findUserBrief(Long userId) {
        NoteUser user = adminMapper.findUserBrief(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // enabled / ai_quota 两列都是 NOT NULL，这里的兜底只是为了让 record 的类型保持基本类型
        return new UserBrief(user.getUsername(), Boolean.TRUE.equals(user.getEnabled()),
                user.getAiQuota() == null ? 0 : user.getAiQuota());
    }

    private UserVo findUserVo(Long userId) {
        Map<String, Object> row = adminMapper.findUserVo(userId);
        if (row == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserVo(row);
    }

    /** 日志文案只需要标题，所以 SQL 只取 id + title；查不到返回 null 由调用方翻成「笔记不存在」 */
    private Note findNoteBrief(Long noteId) {
        Note note = adminMapper.findNoteBrief(noteId);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    private InspectNoteVo findInspectNote(Long noteId) {
        Map<String, Object> row = adminMapper.findInspectNote(noteId);
        if (row == null) {
            throw new BusinessException("笔记不存在");
        }
        InspectNoteVo note = toInspectNoteVo(row);
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

    /**
     * key 是 note_id / tag_id / tag_name。标签列刻意做了别名：原列名 {@code id} 与 note 的 id 同名，
     * 同一条 Map 里两个 id 混着取是这类改造里最容易出的错。
     */
    private Map<Long, List<TagVo>> loadTags(List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        // IN (?, ?, ?) 的占位符个数由 <foreach> 按集合大小生成，仍然只有值走参数
        List<Map<String, Object>> rows = adminMapper.findTagsByNoteIds(noteIds);
        Map<Long, List<TagVo>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long noteId = longVal(row.get("note_id"));
            TagVo tag = new TagVo(longVal(row.get("tag_id")), stringVal(row.get("tag_name")));
            grouped.computeIfAbsent(noteId, key -> new ArrayList<>()).add(tag);
        }
        return grouped;
    }

    private AiConfigVo readAiConfig() {
        NoteAiConfig config = adminMapper.findAiConfig();
        if (config == null) {
            throw new BusinessException("AI 配置不存在，请先执行 note-schema.sql 初始化");
        }
        return new AiConfigVo(
                config.getEndpoint(),
                config.getModel(),
                // max_chars 是 NOT NULL，这里兜 0 只是为了保持「原来是 rs.getInt 拆箱」的等价性
                config.getMaxChars() == null ? 0 : config.getMaxChars(),
                maskKey(config.getApiKey()),
                formatTime(config.getUpdatedAt()));
    }

    /** 查不到返回 null（发布公告后回查、编辑前取旧值都靠这个语义） */
    private AnnouncementVo findAnnouncement(Long id) {
        NoteSystemAnnouncement entity = adminMapper.findAnnouncement(id);
        return entity == null ? null : toAnnouncementVo(entity);
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

    // ------------------------------------------------------------ Map / 实体 -> VO

    /** key：id / username / nickname / email / role / enabled / ai_quota / note_count / created_at */
    private static UserVo toUserVo(Map<String, Object> row) {
        return new UserVo(
                longVal(row.get("id")),
                stringVal(row.get("username")),
                stringVal(row.get("nickname")),
                stringVal(row.get("email")),
                stringVal(row.get("role")),
                boolVal(row.get("enabled")),
                intVal(row.get("ai_quota")),
                longVal(row.get("note_count")),
                formatTime(row.get("created_at")));
    }

    /** key：id / title / content / status / flagged / author_name / updated_at */
    private static InspectNoteVo toInspectNoteVo(Map<String, Object> row) {
        return new InspectNoteVo(
                longVal(row.get("id")),
                stringVal(row.get("title")),
                stringVal(row.get("content")),
                stringVal(row.get("status")),
                boolVal(row.get("flagged")),
                stringVal(row.get("author_name")),
                formatTime(row.get("updated_at")),
                List.of());
    }

    /** key：id / title / content / enabled / created_at */
    private static AnnouncementVo toAnnouncementVo(Map<String, Object> row) {
        return new AnnouncementVo(
                longVal(row.get("id")),
                stringVal(row.get("title")),
                stringVal(row.get("content")),
                boolVal(row.get("enabled")),
                formatTime(row.get("created_at")));
    }

    /** 直接从实体转（findAnnouncement 用的是 resultType="NoteSystemAnnouncement"） */
    private static AnnouncementVo toAnnouncementVo(NoteSystemAnnouncement entity) {
        return new AnnouncementVo(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                Boolean.TRUE.equals(entity.getEnabled()),
                formatTime(entity.getCreatedAt()));
    }

    /** key：id / type / detail / actor_name / ip / created_at */
    private static LogVo toLogVo(Map<String, Object> row) {
        return new LogVo(
                longVal(row.get("id")),
                stringVal(row.get("type")),
                stringVal(row.get("detail")),
                stringVal(row.get("actor_name")),
                stringVal(row.get("ip")),
                formatTime(row.get("created_at")));
    }

    // ------------------------------------------------------------ Map 取值兜底

    /** MyBatis 默认不把 null 列写进 Map，所以「缺 key」与「值为 null」都要能安全取值 */
    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static long longVal(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static int intVal(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * TINYINT(1) 在 Map 里可能是 Boolean（驱动按 tinyInt1isBit 转），也可能是数字，
     * 两种都认；null 视为 false —— 与原来的 {@code rs.getBoolean} 行为一致。
     */
    private static boolean boolVal(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    /**
     * 时间列的格式化。实体里是 LocalDateTime；Map 里可能是 LocalDateTime，
     * 也可能是 Timestamp（取决于驱动版本），两种都认。null 输出空串（与改造前一致）。
     */
    private static String formatTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.format(TIME_FORMAT);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(TIME_FORMAT);
        }
        return "";
    }

    // ------------------------------------------------------------ 其余工具

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
        adminLogMapper.insertAdminLog(adminId, type, clip(detail, DETAIL_MAX), clip(currentIp(), IP_MAX));
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

    /** COUNT(*) 理论上不会返回 null，兜一下避免拆箱 NPE */
    private static long count(Long total) {
        return total == null ? 0L : total;
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

    /**
     * 关键词 -&gt; LIKE 模式。null 表示「不按关键词过滤」：空串 / 全空白都翻成 null，
     * 与改造前的 {@code if (!kw.isEmpty())} 完全等价，SQL 里的 {@code <if>} 直接判断 null。
     */
    private static String likePattern(String keyword) {
        String kw = clean(keyword);
        return kw.isEmpty() ? null : like(kw);
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
