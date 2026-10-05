package org.example.cipherwsserver.service.impl;

import org.example.cipherwsserver.service.AudioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class AudioServiceImpl implements AudioService {

    @Value("${resource.path.audio.music}")
    private String musicsPath;

    @Override
    public ResponseEntity<Resource> streamAudio(String audioName) {
        Path baseDir = Paths.get(musicsPath).toAbsolutePath().normalize();
        Path target = baseDir.resolve(audioName).normalize();

        if (!target.startsWith(baseDir) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音频不存在");
        }

        Resource resource = new FileSystemResource(target);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(audioName, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }
}
