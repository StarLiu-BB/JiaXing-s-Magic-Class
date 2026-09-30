package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.dto.SeckillResultVO;
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

    /** 运营查看活动列表与统计 */
    static final String PERM_LIST = "marketing:seckill:list";
    /** 运营新建/修改/删除/上下线/预热：修复前学生可新建 0.01 元的活动 */
    static final String PERM_EDIT = "marketing:seckill:edit";

    private final SeckillService seckillService;

    @RequirePermission(PERM_LIST)
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

    @RequirePermission(PERM_EDIT)
    @PostMapping
    public R<SeckillActivity> create(@Valid @RequestBody SeckillActivityDTO dto) {
        return R.ok(seckillService.create(dto));
    }

    @RequirePermission(PERM_EDIT)
    @PutMapping
    public R<SeckillActivity> update(@Valid @RequestBody SeckillActivityDTO dto) {
        return R.ok(seckillService.update(dto));
    }

    @RequirePermission(PERM_EDIT)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
        seckillService.deleteByIds(idList);
        return R.ok();
    }

    @RequirePermission(PERM_EDIT)
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody SeckillStatusDTO dto) {
        seckillService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    @RequirePermission(PERM_LIST)
    @GetMapping("/{id}/statistics")
    public R<Map<String, Object>> statistics(@PathVariable Long id) {
        return R.ok(seckillService.statistics(id));
    }

    @RequirePermission(PERM_EDIT)
    @PostMapping("/preload")
    public R<Void> preload(@RequestParam Long activityId) {
        seckillService.preloadStock(activityId);
        return R.ok();
    }

    /** 为当前登录用户签发一次性秒杀令牌；请求中的 userId 参数被忽略。 */
    @RequireLogin
    @PostMapping("/token")
    public R<String> token(@RequestParam Long activityId) {
        return R.ok(seckillService.issueToken(activityId, SecurityUtils.requireUserId()));
    }

    /** 以当前登录用户身份秒杀；成交后返回订单服务中的待支付订单，可直接进入收银台。 */
    @RequireLogin
    @PostMapping("/do")
    public R<SeckillResultVO> doSeckill(@Valid @RequestBody SeckillRequestDTO dto) {
        return R.ok(seckillService.seckill(SecurityUtils.requireUserId(), dto));
    }
}
