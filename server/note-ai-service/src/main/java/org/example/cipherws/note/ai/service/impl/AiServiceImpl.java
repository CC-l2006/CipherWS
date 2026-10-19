package org.example.cipherws.note.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherws.note.ai.dto.AiDtos.SummaryVo;
import org.example.cipherws.note.ai.mapper.AiMapper;
import org.example.cipherws.note.ai.service.AiService;
import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiConfig;
import org.example.cipherws.note.common.entity.NoteAiSummaryRecord;
import org.example.cipherws.note.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 总结实现。
 *
 * 一个刻意的架构取舍：这里直接读 {@code note} 表拿正文，而不是用 OpenFeign
 * 调 note-core-service。原因有两个：
 * 1. 服务之间共享同一个 MySQL 库，直接读省掉一次网络往返；
 * 2. 总结必须基于「库里此刻的正文」，走 Feign 反而要处理对方超时与重试。
 *
 * 代价是 note-ai-service 与 note 表结构耦合了。如果以后要真正拆库，
 * 应该把读正文这一步换成 Feign 契约（放到 note-common 里即可）。
 *
 * 数据访问全部走 {@link AiMapper}（MyBatis）：这一层只管业务顺序
 * （校验 → 调模型 → 扣额度 → 落记录 → 写日志），SQL 与结果集映射都在
 * resources/mapper/AiMapper.xml 里。
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);
    // 与 note-user-service / note-core-service 一致：ISO 8601，Safari 也能解析
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 一次总结固定消耗 1 次额度（需求 4.4.4）。落库与回传都用这个常量，避免两处对不上。 */
    private static final int QUOTA_COST = 1;

    /** 配置表里 max_chars 的兜底值，与建表默认值一致 */
    private static final int DEFAULT_MAX_CHARS = 1200;

    /** 传给模型的原文片段在记录表里的长度上限，列是 VARCHAR(500) */
    private static final int SNIPPET_MAX_LEN = 500;

    private final AiMapper aiMapper;
    private final RestClient http = RestClient.create();

    public AiServiceImpl(AiMapper aiMapper) {
        this.aiMapper = aiMapper;
    }

    @Override
    public SummaryVo summarize(long userId, long noteId) {
        Note note = requireOwnedNote(userId, noteId);
        // 正文列允许 NULL（MEDIUMTEXT NULL），当空串处理：下面 isBlank() 会命中「正文为空」
        String content = note.getContent() == null ? "" : note.getContent();
        String title = note.getTitle() == null ? "" : note.getTitle();
        if (content.isBlank()) {
            throw new BusinessException("正文为空，无法生成摘要");
        }

        NoteAiConfig config = aiConfig();
        String endpoint = config.getEndpoint() == null ? "" : config.getEndpoint();
        String apiKey = config.getApiKey() == null ? "" : config.getApiKey();
        String model = config.getModel() == null ? "" : config.getModel();
        int maxChars = config.getMaxChars() == null ? DEFAULT_MAX_CHARS : config.getMaxChars();

        if (apiKey.isBlank()) {
            // 明确告诉管理员该去哪配，而不是抛一个看不懂的 500
            throw new BusinessException("AI 接口尚未配置密钥，请在「管理后台 → AI 参数」中填写");
        }

        // 需求 4.4 的「单次总结最大字数」：截断的是**送进模型**的原文，不是摘要
        String input = content.length() > maxChars ? content.substring(0, maxChars) : content;
        String summary = callModel(endpoint, apiKey, model, title, input);

        // 原子扣减：把「额度 > 0」写进 WHERE，避免并发下扣成负数。
        // 只看受影响行数，不先查后改。
        int deducted = aiMapper.deductQuota(userId);
        if (deducted == 0) {
            throw new BusinessException("AI 额度已用完，请联系管理员分配");
        }

        String snippet = input.length() > SNIPPET_MAX_LEN ? input.substring(0, SNIPPET_MAX_LEN) : input;
        NoteAiSummaryRecord record = new NoteAiSummaryRecord();
        record.setNoteId(noteId);
        record.setUserId(userId);
        record.setSourceSnippet(snippet);
        record.setSummary(summary);
        record.setQuotaCost(QUOTA_COST);
        // 自增主键由 useGeneratedKeys 回填到 record.id，省掉一次 SELECT LAST_INSERT_ID()
        aiMapper.insertSummaryRecord(record);
        Long recordId = record.getId();

        Integer quotaLeft = aiMapper.findAiQuota(userId);

        writeLog(userId, "AI 总结", "对《" + title + "》调用 AI 总结，消耗 1 次额度");

        return new SummaryVo(recordId, noteId, summary, snippet, QUOTA_COST,
                quotaLeft == null ? 0 : quotaLeft, now());
    }

    @Override
    public List<SummaryVo> history(long userId, long noteId) {
        // 先校验归属，否则能通过 noteId 读到别人的摘要
        requireOwnedNote(userId, noteId);

        List<NoteAiSummaryRecord> records = aiMapper.findSummaryRecords(userId, noteId);
        if (records.isEmpty()) {
            // 没有历史记录就不必再查额度：前端拿到空数组，用不到 quotaLeft
            return List.of();
        }

        // 额度是「用户此刻的剩余次数」，不是每条记录自己的字段，
        // 所以拆成一次单独查询，而不是让每条记录都 JOIN 一份 note_user.ai_quota。
        Integer quota = aiMapper.findAiQuota(userId);
        int quotaLeft = quota == null ? 0 : quota;

        List<SummaryVo> list = new ArrayList<>(records.size());
        for (NoteAiSummaryRecord r : records) {
            list.add(toVo(r, quotaLeft));
        }
        return list;
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    /**
     * 取一篇属于自己的、未删除的笔记。
     * 不存在与「不是你的」抛同一句文案，避免被枚举出别人有多少篇笔记。
     */
    private Note requireOwnedNote(long userId, long noteId) {
        Note note = aiMapper.findActiveNoteById(noteId);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        if (note.getUserId() == null || note.getUserId() != userId) {
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    private NoteAiConfig aiConfig() {
        NoteAiConfig config = aiMapper.findAiConfig();
        if (config == null) {
            throw new BusinessException("AI 参数未初始化，请先导入 note-schema.sql");
        }
        return config;
    }

    /**
     * 实体 -> 对外 VO。
     *
     * 时间在这里才格式化：库里存 LocalDateTime，前端要的是 ISO 8601 字符串。
     * quotaCost 为 NULL 时按 0 处理，与原来 rs.getInt() 的语义一致。
     */
    private static SummaryVo toVo(NoteAiSummaryRecord r, int quotaLeft) {
        return new SummaryVo(
                r.getId(),
                r.getNoteId(),
                r.getSummary(),
                r.getSourceSnippet(),
                r.getQuotaCost() == null ? 0 : r.getQuotaCost(),
                quotaLeft,
                r.getCreatedAt() == null ? null : r.getCreatedAt().format(TIME_FMT));
    }

    /**
     * 调用 OpenAI 兼容的 chat/completions 接口。
     * 不绑定具体厂商：地址与模型都来自 note_ai_config，换模型只改后台配置。
     */
    private String callModel(String endpoint, String apiKey, String model, String title, String input) {
        Map<String, Object> system = Map.of(
                "role", "system",
                "content", "你是一个笔记摘要助手。用中文输出，先一句话概括主旨，再列出要点，不要复述原文。");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(system);
        messages.add(Map.of("role", "user", "content", "标题：" + title + "\n\n正文：\n" + input));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.3);

        String body;
        try {
            body = http.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("调用 AI 接口失败 endpoint={}", endpoint, e);
            // 把「谁的问题」说清楚：是接口不通，不是用户操作错了
            throw new BusinessException("AI 接口调用失败，请检查后台配置的地址与密钥是否正确");
        }

        try {
            JsonNode root = MAPPER.readTree(body == null ? "{}" : body);
            JsonNode text = root.path("choices").path(0).path("message").path("content");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new IllegalStateException("响应里没有 choices[0].message.content");
            }
            return text.asText().trim();
        } catch (Exception e) {
            log.error("解析 AI 响应失败，原文前 500 字：{}",
                    body == null ? "" : body.substring(0, Math.min(500, body.length())), e);
            throw new BusinessException("AI 返回内容无法解析，请检查后台配置的接口是否兼容 OpenAI 格式");
        }
    }

    private void writeLog(long userId, String type, String detail) {
        try {
            aiMapper.insertUserLog(userId, type, detail, "");
        } catch (Exception ignored) {
            // 日志失败不影响总结结果
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }
}
