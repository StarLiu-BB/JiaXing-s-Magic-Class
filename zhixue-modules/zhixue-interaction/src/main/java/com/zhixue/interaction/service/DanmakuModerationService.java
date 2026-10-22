package com.zhixue.interaction.service;

import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Slf4j
@Service
public class DanmakuModerationService {
    public record Result(boolean broadcast, int code, String notice) {}
    private final DanmakuAuditService auditService;
    private final DanmakuProducer producer;
    private final RedisMessagePublisher publisher;
    private final Executor executor;
    private final boolean failOpen;
    private final long maxAgeMs;

    public DanmakuModerationService(DanmakuAuditService auditService, DanmakuProducer producer,
            RedisMessagePublisher publisher, @Qualifier("danmakuAuditExecutor") Executor executor,
            @Value("${interaction.danmaku.fail-open:true}") boolean failOpen,
            @Value("${interaction.danmaku.max-age-ms:30000}") long maxAgeMs) {
        this.auditService = auditService;
        this.producer = producer;
        this.publisher = publisher;
        this.executor = executor;
        this.failOpen = failOpen;
        this.maxAgeMs = maxAgeMs;
    }

    public boolean submit(DanmakuMessageDTO dto, BooleanSupplier active, Consumer<Result> completed) {
        long submitted = System.nanoTime();
        try {
            executor.execute(() -> {
                Result result = moderate(dto, () -> active.getAsBoolean()
                        && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - submitted) < maxAgeMs);
                try { completed.accept(result); }
                catch (RuntimeException unavailable) { log.warn("弹幕发送者结果通知失败"); }
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    public Result moderate(DanmakuMessageDTO dto, BooleanSupplier active) {
        try {
            if (!active.getAsBoolean()) return new Result(false, 503, "弹幕已过期或登录失效，请重试");
            DanmakuAuditService.Decision decision = auditService.audit(dto.getContent());
            dto.setAuditStatus(decision.status());
            dto.setAuditRemark(decision.remark());
            if (decision.status() == 2) {
                producer.send(dto);
                return new Result(false, 400, "弹幕审核未通过");
            }
            if (!active.getAsBoolean()) return new Result(false, 503, "弹幕已过期或登录失效，请重试");
            producer.send(dto);
            if (decision.status() == 0 && !failOpen) {
                return new Result(false, 503, "审核不可用，已记录待人工复核，暂不展示");
            }
            if (!active.getAsBoolean()) return new Result(false, 503, "弹幕已过期或登录失效，未广播");
            publisher.publish(dto);
            return new Result(true, 200, decision.status() == 0 ? "审核不可用，已展示并记录待人工复核" : "");
        } catch (RuntimeException failure) {
            log.error("弹幕审核分发失败 roomId={}, userId={}", dto.getRoomId(), dto.getUserId());
            return new Result(false, 503, "弹幕处理失败，请稍后重试");
        }
    }
}
