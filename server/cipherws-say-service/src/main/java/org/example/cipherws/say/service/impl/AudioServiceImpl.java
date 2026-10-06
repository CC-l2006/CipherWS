package org.example.cipherws.say.service.impl;

import org.example.cipherws.say.service.AudioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

/**
 * 音频流服务。
 *
 * <p>音乐根目录由 {@code resource.path.audio.music} 指定，默认 {@code classpath:music/}；
 * 生产环境可覆盖为磁盘目录（如 {@code /opt/cipherws/music/}）。</p>
 */
@Service
public class AudioServiceImpl implements AudioService {

    @Value("${resource.path.audio.music:classpath:music/}")
    private String musicsPath;

    private final ResourceLoader resourceLoader;

    public AudioServiceImpl(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public ResponseEntity<Resource> streamAudio(String audioName) {
        // 防御路径穿越：只允许单纯的文件名
        if (audioName == null || audioName.isBlank()
                || audioName.contains("..") || audioName.contains("/") || audioName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法音频名称");
        }

        String base = musicsPath.endsWith("/") ? musicsPath : musicsPath + "/";
        Resource resource = resourceLoader.getResource(base + audioName);
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音频不存在");
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(audioName, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }
}
