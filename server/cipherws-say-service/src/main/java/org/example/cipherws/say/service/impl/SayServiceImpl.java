package org.example.cipherws.say.service.impl;

import jakarta.annotation.Resource;
import org.example.cipherws.common.entity.Say;
import org.example.cipherws.common.result.Result;
import org.example.cipherws.say.service.SayService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SayServiceImpl implements SayService {

    @Resource
    private List<Say> sayList;

    @Override
    public Result querySayingById(int sayId) {
        if (sayId < 1 || sayId > sayList.size()) {
            return Result.fail("id错误");
        }
        return Result.ok(sayList.get(sayId - 1));
    }
}
