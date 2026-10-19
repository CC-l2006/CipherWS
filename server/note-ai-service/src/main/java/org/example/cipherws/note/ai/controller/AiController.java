package org.example.cipherws.note.ai.controller;

import org.example.cipherws.note.ai.dto.AiDtos.SummarizeReq;
import org.example.cipherws.note.ai.dto.AiDtos.SummaryVo;
import org.example.cipherws.note.ai.service.AiService;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 发起总结。一次调用消耗 1 次额度（需求 4.4.4） */
    @PostMapping("/summary")
    public Result summarize(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestBody SummarizeReq req) {
        if (req.noteId() == null) {
            throw new BusinessException("缺少笔记 ID");
        }
        return Result.ok(aiService.summarize(requireUserId(userId), req.noteId()));
    }

    /** 某篇笔记的历史总结（需求 4.4.3） */
    @GetMapping("/summary")
    public Result history(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestParam Long noteId) {
        List<SummaryVo> list = aiService.history(requireUserId(userId), noteId);
        return Result.ok(list, (long) list.size());
    }

    private long requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return userId;
    }
}
