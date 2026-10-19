package org.example.cipherws.note.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 笔记系统 AI 服务：8086。
 *
 * 只负责「把笔记发给模型、拿回摘要、扣一次额度」这条链路。
 * AI 的接口地址/密钥/字数限制都存在 note_ai_config 表里，管理员在后台改完立即生效，
 * 不需要重启 —— 这是需求 6.4.2 明确要求的。
 *
 * @MapperScan 显式声明 Mapper 接口所在包：靠 @Mapper 注解自动发现也能工作，
 * 但一旦有人把接口挪到别的包就会静默失效，显式写死更稳。
 */
@SpringBootApplication(scanBasePackages = "org.example.cipherws.note")
@EnableDiscoveryClient
@MapperScan("org.example.cipherws.note.ai.mapper")
public class NoteAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteAiServiceApplication.class, args);
    }
}
