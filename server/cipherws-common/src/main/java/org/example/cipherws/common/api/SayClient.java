package org.example.cipherws.common.api;

import org.example.cipherws.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * say 服务的 OpenFeign 契约。
 *
 * <p>约定：契约定义在公共模块，服务提供方（cipherws-say-service）的
 * Controller 直接实现该接口，调用方（cipherws-gateway）通过
 * {@code @EnableFeignClients(basePackages = "org.example.cipherws.common.api")}
 * 引入后即可注入使用。请求会经 Nacos 服务发现 + Spring Cloud LoadBalancer
 * 负载均衡到具体的服务实例。</p>
 */
@FeignClient(name = "cipherws-say-service", path = "/say", contextId = "sayClient")
public interface SayClient {

    @GetMapping("/saying")
    Result saying(@RequestParam("id") int sayId);
}
