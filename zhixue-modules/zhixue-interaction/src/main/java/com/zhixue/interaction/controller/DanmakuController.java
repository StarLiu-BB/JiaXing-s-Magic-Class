package com.zhixue.interaction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.interaction.domain.dto.DanmakuAuditDTO;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 弹幕 HTTP 接口。
 *
 * <p>弹幕广播前审核：通过=1，拒绝=2，审核不可用=0待复核；这里提供人工复核：
 * 恢复被误拒的弹幕，或下架已通过的弹幕。</p>
 */
@RestController
@RequestMapping("/danmaku")
@RequiredArgsConstructor
public class DanmakuController {

    /** 浏览全站弹幕（含被拒内容与发送者）：修复前任何登录用户都能翻看 */
    static final String PERM_LIST = "interaction:danmaku:list";
    static final String PERM_AUDIT = "interaction:danmaku:audit";

    private final DanmakuMapper danmakuMapper;

    @org.springframework.beans.factory.annotation.Value("${interaction.danmaku.fail-open:true}")
    private boolean failOpen;

    @RequirePermission(PERM_LIST)
    @GetMapping("/policy")
    public R<java.util.Map<String, Object>> policy() {
        return R.ok(java.util.Map.of("failOpen", failOpen));
    }

    /**
     * 播放页拉取某个房间已通过的历史弹幕（最近 N 条）；登录由网关校验，不需要额外权限。
     */
    @GetMapping("/history")
    public R<List<Danmaku>> history(@RequestParam Long roomId,
                                    @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<Danmaku> qw = new LambdaQueryWrapper<>();
        qw.eq(Danmaku::getRoomId, roomId)
          .eq(Danmaku::getAuditStatus, 1)
          .orderByDesc(Danmaku::getCreateTime)
          .last("limit " + safeLimit);
        return R.ok(danmakuMapper.selectList(qw));
    }

    /**
     * 分页查询弹幕（管理后台），最新的在前。
     */
    @RequirePermission(PERM_LIST)
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
        qw.orderByDesc(Danmaku::getCreateTime);
        Page<Danmaku> page = danmakuMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getSize()));
    }

    /**
     * 人工复核：通过则重新出现在历史弹幕中并清除拒绝原因；拒绝须填写原因。
     */
    @RequirePermission(PERM_AUDIT)
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody DanmakuAuditDTO dto) {
        try {
            return applyAudit(id, dto);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "人工复核存储不可用，请重试");
        }
    }

    private R<Void> applyAudit(Long id, DanmakuAuditDTO dto) {
        Danmaku danmaku = danmakuMapper.selectById(id);
        if (danmaku == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "弹幕不存在");
        }
        boolean reject = dto.getAuditStatus() == 2;
        if (reject && !StringUtils.hasText(dto.getAuditRemark())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "拒绝时请填写原因");
        }
        // 必须显式 SET：updateById 会跳过 null 字段，"通过"时拒绝原因清不掉（界面验证实测）
        int updated = danmakuMapper.update(null, new LambdaUpdateWrapper<Danmaku>()
                .eq(Danmaku::getId, id)
                .set(Danmaku::getAuditStatus, dto.getAuditStatus())
                .set(Danmaku::getAuditRemark, reject ? dto.getAuditRemark().trim() : null));
        if (updated != 1) throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "人工复核保存失败，请重试");
        return R.ok();
    }
}
