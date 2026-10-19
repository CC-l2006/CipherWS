package org.example.cipherws.note.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherws.note.ai.dto.AiDtos.SummaryVo;
import org.example.cipherws.note.ai.service.AiService;
import org.example.cipherws.note.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);
    // 与 note-user-service / note-core-service 一致：ISO 8601，Safari 也能解析
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final RestClient http = RestClient.create();

    public AiServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SummaryVo summarize(long userId, long noteId) {
        Map<String, Object> note = requireOwnedNote(userId, noteId);
        String content = String.valueOf(note.getOrDefault("content", ""));
        String title = String.valueOf(note.getOrDefault("title", ""));
        if (content.isBlank()) {
            throw new BusinessException("正文为空，无法生成摘要");
        }

        Map<String, Object> config = aiConfig();
        String endpoint = String.valueOf(config.getOrDefault("endpoint", ""));
        String apiKey = String.valueOf(config.getOrDefault("api_key", ""));
        String model = String.valueOf(config.getOrDefault("model", ""));
        int maxChars = ((Number) config.getOrDefault("max_chars", 1200)).intValue();

        if (apiKey.isBlank()) {
            // 明确告诉管理员该去哪配，而不是抛一个看不懂的 500
            throw new BusinessException("AI 接口尚未配置密钥，请在「管理后台 → AI 参数」中填写");
        }

        // 需求 4.4 的「单次总结最大字数」：截断的是**送进模型**的原文，不是摘要
        String input = content.length() > maxChars ? content.substring(0, maxChars) : content;
        String summary = callModel(endpoint, apiKey, model, title, input);

        // 原子扣减：把「额度 > 0」写进 WHERE，避免并发下扣成负数
        int deducted = jdbc.update(
                "UPDATE `note_user` SET ai_quota = ai_quota - 1 WHERE id = ? AND ai_quota > 0", userId);
        if (deducted == 0) {
            throw new BusinessException("AI 额度已用完，请联系管理员分配");
        }

        String snippet = input.length() > 500 ? input.substring(0, 500) : input;
        jdbc.update("""
                INSERT INTO note_ai_summary_record (note_id, user_id, source_snippet, summary, quota_cost)
                VALUES (?, ?, ?, ?, 1)
                """, noteId, userId, snippet, summary);

        Integer quotaLeft = jdbc.queryForObject(
                "SELECT ai_quota FROM `note_user` WHERE id = ?", Integer.class, userId);
        Long recordId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        writeLog(userId, "AI 总结", "对《" + title + "》调用 AI 总结，消耗 1 次额度");

        return new SummaryVo(recordId, noteId, summary, snippet, 1,
                quotaLeft == null ? 0 : quotaLeft, now());
    }

    @Override
    public List<SummaryVo> history(long userId, long noteId) {
        // 先校验归属，否则能通过 noteId 读到别人的摘要
        requireOwnedNote(userId, noteId);
        return jdbc.query("""
                SELECT r.id, r.note_id, r.summary, r.source_snippet, r.quota_cost, r.created_at,
                       u.ai_quota
                FROM note_ai_summary_record r
                JOIN `note_user` u ON u.id = r.user_id
                WHERE r.user_id = ? AND r.note_id = ?
                ORDER BY r.created_at DESC
                """, (rs, i) -> new SummaryVo(
                rs.getLong("id"),
                rs.getLong("note_id"),
                rs.getString("summary"),
                rs.getString("source_snippet"),
                rs.getInt("quota_cost"),
                rs.getInt("ai_quota"),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime().format(TIME_FMT)), userId, noteId);
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    private Map<String, Object> requireOwnedNote(long userId, long noteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, user_id, title, content FROM `note` WHERE id = ? AND deleted = 0", noteId);
        if (rows.isEmpty()) {
            throw new BusinessException("笔记不存在");
        }
        Map<String, Object> note = rows.get(0);
        if (((Number) note.get("user_id")).longValue() != userId) {
            // 不区分「不存在」与「不是你的」，避免被枚举出别人有多少篇笔记
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    private Map<String, Object> aiConfig() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT endpoint, api_key, model, max_chars FROM note_ai_config WHERE id = 1");
        if (rows.isEmpty()) {
            throw new BusinessException("AI 参数未初始化，请先导入 note-schema.sql");
        }
        return rows.get(0);
    }

    /**
     * 调用 OpenAI 兼容的 chat/completions 接口。
     * 不绑定具体厂商：地址与模型都来自 ai_config，换模型只改后台配置。
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
            jdbc.update("INSERT INTO note_user_operation_log (user_id, type, detail, ip) VALUES (?, ?, ?, ?)",
                    userId, type, detail, "");
        } catch (Exception ignored) {
            // 日志失败不影响总结结果
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }
}
