package org.example.cipherwsserver.controller;

import org.springframework.core.io.Resource;
import org.example.cipherwsserver.service.AudioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audio")
public class AudioController {

    @Autowired
    private AudioService audioService;

    @GetMapping("/stream")
    public ResponseEntity<Resource> streamAudio(@RequestParam("audio") String audioName){
        return audioService.streamAudio(audioName);
    }

}
