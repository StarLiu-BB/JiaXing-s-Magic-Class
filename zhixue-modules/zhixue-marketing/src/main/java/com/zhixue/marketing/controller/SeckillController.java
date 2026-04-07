package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.dto.SeckillStatusDTO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 秒杀接口。
 */
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @GetMapping("/list")
    public R<PageResult<SeckillActivity>> list(PageQuery pageQuery,
                                               @RequestParam(required = false) String title,
                                               @RequestParam(required = false) Integer status) {
        return R.ok(seckillService.pageActivities(pageQuery, title, status));
    }

    @GetMapping("/online")
    public R<List<SeckillActivity>> online() {
        return R.ok(seckillService.listOnline());
    }

    @GetMapping("/{id}")
    public R<SeckillActivity> get(@PathVariable Long id) {
        return R.ok(seckillService.getById(id));
    }

    @PostMapping
    public R<SeckillActivity> create(@Valid @RequestBody SeckillActivityDTO dto) {
        return R.ok(seckillService.create(dto));
    }

    @PutMapping
    public R<SeckillActivity> update(@Valid @RequestBody SeckillActivityDTO dto) {
        return R.ok(seckillService.update(dto));
    }

    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
        seckillService.deleteByIds(idList);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody SeckillStatusDTO dto) {
        seckillService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    @GetMapping("/{id}/statistics")
    public R<Map<String, Object>> statistics(@PathVariable Long id) {
        return R.ok(seckillService.statistics(id));
    }

    @PostMapping("/preload")
    public R<Void> preload(@RequestParam Long activityId) {
        seckillService.preloadStock(activityId);
        return R.ok();
    }

    @PostMapping("/token")
    public R<String> token(@RequestParam Long activityId, @RequestParam Long userId) {
        return R.ok(seckillService.issueToken(activityId, userId));
    }

    @PostMapping("/do")
    public R<String> doSeckill(@Valid @RequestBody SeckillRequestDTO dto) {
        return R.ok(seckillService.seckill(dto));
    }
}
