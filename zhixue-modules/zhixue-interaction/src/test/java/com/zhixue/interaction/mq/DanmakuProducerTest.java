package com.zhixue.interaction.mq;

import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.service.DanmakuPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanmakuProducerTest {

    @Test
    void shouldPersistDirectlyWhenStubMode() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
        DanmakuPersistenceService persistenceService = mock(DanmakuPersistenceService.class);
        DanmakuProducer producer = new DanmakuProducer(provider, persistenceService, Jackson2ObjectMapperBuilder.json().build());
        ReflectionTestUtils.setField(producer, "mqMode", "stub");

        DanmakuMessageDTO dto = new DanmakuMessageDTO();
        dto.setRoomId(1L);
        dto.setUserId(2L);
        dto.setContent("hello");

        producer.send(dto);

        verify(persistenceService).persist(dto);
        verify(provider, never()).getIfAvailable();
    }

    @Test
    void shouldFallbackToPersistenceWhenRabbitTemplateMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        DanmakuPersistenceService persistenceService = mock(DanmakuPersistenceService.class);
        DanmakuProducer producer = new DanmakuProducer(provider, persistenceService, Jackson2ObjectMapperBuilder.json().build());
        ReflectionTestUtils.setField(producer, "mqMode", "sandbox");

        DanmakuMessageDTO dto = new DanmakuMessageDTO();
        dto.setRoomId(1L);
        dto.setUserId(2L);
        dto.setContent("hello");

        producer.send(dto);

        verify(persistenceService).persist(dto);
    }

    @Test
    void shouldSendToRabbitWhenMqAvailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rabbitTemplate);
        org.mockito.Mockito.doAnswer(call -> {
            org.springframework.amqp.rabbit.connection.CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(String.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
        DanmakuPersistenceService persistenceService = mock(DanmakuPersistenceService.class);
        DanmakuProducer producer = new DanmakuProducer(provider, persistenceService, Jackson2ObjectMapperBuilder.json().build());
        ReflectionTestUtils.setField(producer, "mqMode", "sandbox");
        ReflectionTestUtils.setField(producer, "exchange", "ex");
        ReflectionTestUtils.setField(producer, "routingKey", "rk");

        DanmakuMessageDTO dto = new DanmakuMessageDTO();
        dto.setRoomId(1L);
        dto.setUserId(2L);
        dto.setContent("hello");

        producer.send(dto);

        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(String.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
        verify(persistenceService, never()).persist(dto);
    }
}
