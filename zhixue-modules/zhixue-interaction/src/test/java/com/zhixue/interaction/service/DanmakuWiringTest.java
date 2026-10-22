package com.zhixue.interaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.interaction.mapper.DanmakuMapper;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DanmakuWiringTest {
    @Test void productionBeansWireWithoutCircularDependencyAndExecutorIsBounded() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RemoteAiService.class, () -> mock(RemoteAiService.class));
            context.registerBean(DanmakuMapper.class, () -> mock(DanmakuMapper.class));
            context.registerBean(SecurityProperties.class, SecurityProperties::new);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
            context.register(DanmakuExecutorConfig.class, DanmakuAuditService.class, DanmakuPersistenceService.class,
                    DanmakuProducer.class, RedisMessagePublisher.class, DanmakuModerationService.class);
            context.refresh();
            assertThat(context.getBean(DanmakuModerationService.class)).isNotNull();
            var executor = context.getBean("danmakuAuditExecutor", java.util.concurrent.ThreadPoolExecutor.class);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(200);
            assertThat(executor.getRejectedExecutionHandler()).isInstanceOf(java.util.concurrent.ThreadPoolExecutor.AbortPolicy.class);
        }
    }
}
