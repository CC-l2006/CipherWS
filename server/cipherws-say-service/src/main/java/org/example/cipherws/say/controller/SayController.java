package org.example.cipherws.say.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.common.api.SayClient;
import org.example.cipherws.common.result.Result;
import org.example.cipherws.say.service.SayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * say 服务提供方：直接实现公共模块里的 OpenFeign 契约 {@link SayClient}，
 * 从而保证服务端实现与调用方契约绝对一致。
 */
@RestController
@RequestMapping("/say")
public class SayController implements SayClient {

    @Resource
    private SayService sayService;

    @Override
    @GetMapping("/saying")
    public Result saying(@RequestParam("id") int sayId) {
        return sayService.querySayingById(sayId);
    }
}
