package org.example.cipherws.say.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherws.common.entity.Say;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 加载 classpath 下的 saying.json，注册为 {@code sayList} Bean。
 *
 * <p>相比原实现改为从 classpath 读取，避免依赖运行目录下的
 * {@code src/main/resources} 相对路径，打包成可执行 jar 后同样可用。</p>
 */
@Configuration
public class SayingConfig {

    @Bean(name = "sayList")
    public List<Say> sayList() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("saying.json").getInputStream()) {
            List<Say> list = objectMapper.readValue(in, new TypeReference<List<Say>>() {
            });
            if (list == null) {
                throw new IllegalStateException("saying.json 读取失败");
            }
            return list;
        }
    }
}
