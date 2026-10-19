package org.example.cipherws.note.ai.service;

import org.example.cipherws.note.ai.dto.AiDtos.SummaryVo;

import java.util.List;

/**
 * AI 智能总结（需求 4.4）。
 */
public interface AiService {

    /** 对一篇笔记发起总结：调模型 → 落记录 → 扣额度 */
    SummaryVo summarize(long userId, long noteId);

    /** 某篇笔记的历史总结，按时间倒序（需求 4.4.3） */
    List<SummaryVo> history(long userId, long noteId);
}
