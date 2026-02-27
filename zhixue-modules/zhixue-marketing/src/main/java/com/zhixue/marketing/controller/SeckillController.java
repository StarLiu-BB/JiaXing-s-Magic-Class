package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀接口。
 */
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @GetMapping("/list")
    public R<List<SeckillActivity>> list() {
        return R.ok(seckillService.listOnline());
    }

    @PostMapping("/preload")
    public R<Void> preload(@RequestParam Long activityId) {
        seckillService.preloadStock(activityId);
        return R.ok();
    }

    @PostMapping("/do")
    public R<String> doSeckill(@Valid @RequestBody SeckillRequestDTO dto) {
        return R.ok(seckillService.seckill(dto));
    }
}


