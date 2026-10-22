package com.zhixue.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.interaction.domain.entity.Danmaku;
import org.apache.ibatis.annotations.Mapper;

/**
 * 弹幕 Mapper。
 */
@Mapper
public interface DanmakuMapper extends BaseMapper<Danmaku> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM danmaku WHERE message_id = #{messageId}")
    Danmaku findMessage(@org.apache.ibatis.annotations.Param("messageId") String messageId);
}


