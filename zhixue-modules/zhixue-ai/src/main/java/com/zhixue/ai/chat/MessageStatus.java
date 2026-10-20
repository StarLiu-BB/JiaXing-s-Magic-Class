package com.zhixue.ai.chat;

/** ai_message.status；学生消息恒为 COMPLETED */
public final class MessageStatus {

    public static final int COMPLETED = 1;
    public static final int STOPPED = 2;
    public static final int FAILED = 3;

    private MessageStatus() {
    }
}
