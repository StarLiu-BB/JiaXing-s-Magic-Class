package com.zhixue.system.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.system.domain.entity.SysDictData;
import com.zhixue.system.domain.entity.SysDictType;
import com.zhixue.system.service.SysDictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理接口。
 *
 * <p>此前本类是占位实现：saveType/saveData 只打印日志便返回
 * {@code R.ok()}，对调用方声称保存成功却从不落库。</p>
 */
@Slf4j
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    @RequireLogin
    @GetMapping("/type/list")
    public R<PageResult<SysDictType>> pageTypes(PageQuery query,
                                                @RequestParam(required = false) String dictName,
                                                @RequestParam(required = false) Integer status) {
        return R.ok(dictService.pageTypes(query, dictName, status));
    }

    @RequireLogin
    @GetMapping("/type/all")
    public R<List<SysDictType>> listTypes() {
        return R.ok(dictService.listTypes());
    }

    @RequirePermission("system:dict:edit")
    @PostMapping("/type/save")
    public R<SysDictType> saveType(@Valid @RequestBody SysDictType dictType) {
        return R.ok(dictService.saveType(dictType));
    }

    @RequirePermission("system:dict:edit")
    @DeleteMapping("/type/{id}")
    public R<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return R.ok();
    }

    /** 按类型编码取数据项，供前端下拉使用。 */
    @RequireLogin
    @GetMapping("/data/type/{dictType}")
    public R<List<SysDictData>> listDataByType(@PathVariable String dictType) {
        return R.ok(dictService.listDataByType(dictType));
    }

    @RequirePermission("system:dict:edit")
    @PostMapping("/data/save")
    public R<SysDictData> saveData(@Valid @RequestBody SysDictData dictData) {
        return R.ok(dictService.saveData(dictData));
    }

    @RequirePermission("system:dict:edit")
    @DeleteMapping("/data/{id}")
    public R<Void> deleteData(@PathVariable Long id) {
        dictService.deleteData(id);
        return R.ok();
    }
}
