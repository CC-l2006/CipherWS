package org.example.cipherws.note.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 笔记核心服务启动类（端口 8085）：笔记增删改查、回收站、标签。
 *
 * <p>扫描范围必须放宽到 {@code org.example.cipherws.note}：本服务的 Bean 在
 * {@code note.core} 下，而 note-common 里的 {@code GlobalExceptionHandler} 在
 * {@code note.common} 下。只扫 {@code note.core} 的话异常处理器注册不上，
 * 抛 {@code BusinessException} 时走的是 Spring 默认错误页，
 * 前端读不到 errMessage，用户只会看到一句笼统的「请求失败」。</p>
 *
 * <p>{@code @MapperScan} 显式声明 Mapper 接口所在包：靠 {@code @Mapper} 注解自动发现
 * 也能工作，但一旦有人把接口挪到别的包就会静默失效（启动时报
 * Invalid bound statement），显式写死更稳。</p>
 */
@SpringBootApplication(scanBasePackages = "org.example.cipherws.note")
@MapperScan("org.example.cipherws.note.core.mapper")
public class NoteCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteCoreServiceApplication.class, args);
    }
}
