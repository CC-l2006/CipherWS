package org.example.cipherwsserver.service.impl;

import jakarta.annotation.Resource;
import org.example.cipherwsserver.entity.Say;
import org.example.cipherwsserver.service.SayService;
import org.example.cipherwsserver.utils.Result;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SayServiceImpl implements SayService {

    @Resource
    private List<Say> sayList;

    @Override
    public Result querySayingById(int sayId) {
        Say say;
        say = sayList.get(sayId - 1);
        if (say == null){
            return Result.fail("id错误");
        }
        return Result.ok(say);
    }
}
