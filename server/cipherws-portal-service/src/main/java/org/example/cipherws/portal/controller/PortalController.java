package org.example.cipherws.portal.controller;

import org.example.cipherws.common.api.SayClient;
import org.example.cipherws.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenFeign 调用示例：注入公共模块的 {@link SayClient} 契约，
 * 通过 Nacos 服务发现 + 负载均衡调用 {@code cipherws-say-service}。
 *
 * <p>对外接口：{@code GET /portal/say/{id}}，返回结果与直连
 * {@code /say/saying?id=} 完全一致，用于验证 OpenFeign 链路是否打通。</p>
 */
@RestController
@RequestMapping("/portal")
public class PortalController {

    private final SayClient sayClient;

    public PortalController(SayClient sayClient) {
        this.sayClient = sayClient;
    }

    @GetMapping("/say/{id}")
    public Result say(@PathVariable("id") int id) {
        return sayClient.saying(id);
    }
}
