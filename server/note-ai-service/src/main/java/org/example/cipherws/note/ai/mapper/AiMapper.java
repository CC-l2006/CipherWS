package org.example.cipherws.note.ai.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiConfig;
import org.example.cipherws.note.common.entity.NoteAiSummaryRecord;

import java.util.List;

/**
 * AI 总结链路的数据访问接口。SQL 全部在 resources/mapper/AiMapper.xml 里。
 *
 * 这个模块只碰四张表：note（读正文）、note_ai_config（读模型参数）、
 * note_ai_summary_record（写/读摘要）、note_user（扣额度/查额度），
 * 外加一张 note_user_operation_log（写操作日志）。
 *
 * 为什么不像原来那样用 JdbcTemplate 直接拼 SQL：SQL 挪到 XML 后，
 * 实体映射（ai_quota -> aiQuota 之类）由 MyBatis 按列名自动完成，
 * Service 里不再有一堆 rs.getString("xxx") 这种字符串键，改列名时编译期就能发现问题。
 */
public interface AiMapper {

    /**
     * 按 id 查一篇**未删除**的笔记。只取归属校验与调模型要用的四列，
     * 不把整行（status/flagged/时间戳）捞出来。
     */
    Note findActiveNoteById(@Param("noteId") long noteId);

    /**
     * 读生效的那一行 AI 配置。约定 id=1 生效（表是单行配置表），
     * 所以 id 写死在 XML 里，不给调用方传参，避免有人传错 id 拿到别的行。
     */
    NoteAiConfig findAiConfig();

    /**
     * 原子扣减一次额度：把「ai_quota > 0」写进 WHERE，
     * 并发下 MySQL 行锁保证要么扣成功、要么一行都不受影响，绝不会扣成负数。
     *
     * @return 受影响行数，0 表示额度已用完
     */
    int deductQuota(@Param("userId") long userId);

    /** 查剩余额度。用户不存在（或额度列为 NULL）时返回 null。 */
    Integer findAiQuota(@Param("userId") long userId);

    /** 插入一条总结记录，自增主键会回填到实体的 id 上（XML 里配了 useGeneratedKeys） */
    int insertSummaryRecord(NoteAiSummaryRecord record);

    /**
     * 某用户某篇笔记的全部总结记录，按创建时间倒序。
     * 这里**不** JOIN note_user 取额度：额度是「用户维度的当前值」，
     * 每条记录都带一份只会让结果集变宽，拆成一次单独查询更清楚。
     */
    List<NoteAiSummaryRecord> findSummaryRecords(@Param("userId") long userId,
                                                 @Param("noteId") long noteId);

    /** 写用户操作日志（需求 4.7.1）。调用方会吞掉异常，日志失败不影响总结结果。 */
    int insertUserLog(@Param("userId") long userId,
                      @Param("type") String type,
                      @Param("detail") String detail,
                      @Param("ip") String ip);
}
