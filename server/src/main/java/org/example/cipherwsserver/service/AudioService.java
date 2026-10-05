package org.example.cipherwsserver.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface AudioService {
    ResponseEntity<Resource> streamAudio(String audioName);
}
