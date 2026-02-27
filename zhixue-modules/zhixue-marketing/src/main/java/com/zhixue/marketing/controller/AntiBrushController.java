package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.service.AntiBrushService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/anti-brush")
@RequiredArgsConstructor
public class AntiBrushController {

    private final AntiBrushService antiBrushService;

    @PostMapping("/check")
    public R<Boolean> checkAntiBrush(@RequestBody @NotBlank Long userId,
                                           @RequestBody @NotBlank String ip,
                                           @RequestBody @NotBlank String deviceId) {
        boolean allowed = antiBrushService.check(userId, ip, deviceId);
        return R.ok(allowed);
    }
}
