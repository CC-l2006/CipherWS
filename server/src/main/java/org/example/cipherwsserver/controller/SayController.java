package org.example.cipherwsserver.controller;


import jakarta.annotation.Resource;
import org.example.cipherwsserver.service.SayService;
import org.example.cipherwsserver.utils.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/say")
public class SayController {

    @Resource
    private SayService sayService;

    @GetMapping("/saying")
    public Result saying(@RequestParam("id") int sayId){
        return sayService.querySayingById(sayId);
    }
}
