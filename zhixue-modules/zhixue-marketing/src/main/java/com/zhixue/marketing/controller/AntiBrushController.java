package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.domain.dto.AntiBrushCheckDTO;
import com.zhixue.marketing.service.AntiBrushService;
import jakarta.validation.Valid;
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
    public R<Boolean> checkAntiBrush(@Valid @RequestBody AntiBrushCheckDTO dto) {
        boolean allowed = antiBrushService.check(dto.getUserId(), dto.getIp(), dto.getDeviceId());
        return R.ok(allowed);
    }
}
