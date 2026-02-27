package com.zhixue.system.controller;

import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <p>
 * 字典管理接口（占位，可后续接入具体实现）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/dict")
public class SysDictController {

    @PostMapping("/type/save")
    public R<Void> saveType(@RequestBody Map<String, Object> body) {
        log.info("保存字典类型: {}", body);
        return R.ok();
    }

    @PostMapping("/data/save")
    public R<Void> saveData(@RequestBody Map<String, Object> body) {
        log.info("保存字典数据: {}", body);
        return R.ok();
    }
}

