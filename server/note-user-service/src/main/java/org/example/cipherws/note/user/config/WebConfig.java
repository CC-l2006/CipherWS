package org.example.cipherws.note.user.config;

import org.example.cipherws.note.user.service.AvatarService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把上传目录挂成静态资源：{@code GET /uploads/**} 直接吐文件。
 *
 * 为什么不用 controller 读文件返回：<img src> 带不了 Authorization 头，
 * 走 controller 就得额外做一套鉴权；而头像本来就是「拿到 URL 就能看」的东西。
 * 文件名是 用户id-随机串，不可枚举，所以这里不再加鉴权。
 *
 * 生产环境更推荐让 nginx 直接指到这个目录（少一层 Java 转发），
 * 网关那边也已经把 /uploads/** 放行并路由到本服务，两种方式都能用。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AvatarService avatarService;

    public WebConfig(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = avatarService.getRoot().toUri().toString();
        // Spring 要求目录型 location 以 / 结尾，否则会把它当成单个文件
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
