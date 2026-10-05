package org.example.cipherws.portal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试。
 *
 * <p>关闭 Nacos 注册与配置导入，保证在没有 Nacos 服务端时单测仍可运行。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.config.import="
})
class PortalServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
