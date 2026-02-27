package com.zhixue.interaction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史弹幕 HTTP 接口。
 */
@RestController
@RequestMapping("/danmaku")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuMapper danmakuMapper;

    /**
     * 查询某个房间的历史弹幕（简单返回最近 N 条）。
     */
    @GetMapping("/history")
    public R<List<Danmaku>> history(@RequestParam Long roomId,
                                    @RequestParam(defaultValue = "50") Integer limit) {
        LambdaQueryWrapper<Danmaku> qw = new LambdaQueryWrapper<>();
        qw.eq(Danmaku::getRoomId, roomId)
          .eq(Danmaku::getAuditStatus, 1)
          .orderByDesc(Danmaku::getCreateTime)
          .last("limit " + limit);
        return R.ok(danmakuMapper.selectList(qw));
    }

    /**
     * 分页查询弹幕（管理后台）。
     */
    @GetMapping("/page")
    public R<PageResult<Danmaku>> page(PageQuery query,
                                       @RequestParam(required = false) Long roomId,
                                       @RequestParam(required = false) Integer auditStatus) {
        LambdaQueryWrapper<Danmaku> qw = new LambdaQueryWrapper<>();
        if (roomId != null) {
            qw.eq(Danmaku::getRoomId, roomId);
        }
        if (auditStatus != null) {
            qw.eq(Danmaku::getAuditStatus, auditStatus);
        }
        Page<Danmaku> page = danmakuMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getSize()));
    }
}

