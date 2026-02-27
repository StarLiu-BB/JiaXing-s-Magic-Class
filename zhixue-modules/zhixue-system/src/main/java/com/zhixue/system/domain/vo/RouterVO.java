package com.zhixue.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 前端路由对象。
 * </p>
 */
@Data
public class RouterVO {

    private String name;
    private String path;
    private String component;
    private String icon;
    private boolean hidden;
    private List<RouterVO> children = new ArrayList<>();
}

