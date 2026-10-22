package com.zhixue.interaction.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 弹幕消息 DTO。
 */
@Data
public class DanmakuMessageDTO {

    /**
     * 房间ID（课程/视频ID）。
     */
    private Long roomId;

    /**
     * 发送用户ID。
     */
    private Long userId;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 视频时间点（秒）。
     */
    private Integer timePoint;

    /**
     * 发送时间（服务器时间）。
     */
    private LocalDateTime sendTime;

    /** 服务端审核结果：0待复核，1通过，2拒绝。WS客户端传入值不可信。 */
    private Integer auditStatus;
    private String auditRemark;
}


