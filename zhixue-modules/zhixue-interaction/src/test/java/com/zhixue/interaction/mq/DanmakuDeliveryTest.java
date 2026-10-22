package com.zhixue.interaction.mq;

import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.service.DanmakuPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuDeliveryTest {
    @Test void nackAndTimeoutMustFallbackToIdempotentStorage() {
        for (boolean nack : new boolean[]{true, false}) {
            RabbitTemplate rabbit = mock(RabbitTemplate.class);
            doAnswer(call -> {
                CorrelationData correlation = call.getArgument(3);
                if (nack) correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
                return null;
            }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));
            @SuppressWarnings("unchecked") ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(rabbit);
            var persistence = mock(DanmakuPersistenceService.class);
            var producer = new DanmakuProducer(provider, persistence, Jackson2ObjectMapperBuilder.json().build());
            ReflectionTestUtils.setField(producer, "exchange", "ex"); ReflectionTestUtils.setField(producer, "routingKey", "rk");
            ReflectionTestUtils.setField(producer, "confirmTimeoutMs", 10L);
            var dto = new DanmakuMessageDTO(); producer.send(dto);
            assertThat(dto.getMessageId()).isNotBlank(); verify(persistence).persist(dto);
        }
    }

    @Test void failedErrorQueueConfirmOrReturnMustRequeueOriginal() {
        for (int mode : new int[]{0, 1, 2}) {
            RabbitTemplate rabbit = mock(RabbitTemplate.class);
            doAnswer(call -> {
                CorrelationData correlation = call.getArgument(3);
                if (mode == 1) correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
                if (mode == 2) {
                    correlation.setReturned(new org.springframework.amqp.core.ReturnedMessage(call.getArgument(2), 312, "no route", "ex", "failed"));
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                }
                return null;
            }).when(rabbit).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class), any(CorrelationData.class));
            var recovery = new DanmakuFailureRecoverer(rabbit, "ex", "failed", 10);
            var message = new org.springframework.amqp.core.Message("evidence".getBytes(), new org.springframework.amqp.core.MessageProperties());
            assertThatThrownBy(() -> recovery.recover(message, new RuntimeException("private detail")))
                    .isInstanceOf(org.springframework.amqp.ImmediateRequeueAmqpException.class);
        }
    }

    @Test void exhaustedConsumerRetryPreservesOriginalPayloadWithConfirmedErrorQueue() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3); data.getFuture().complete(new CorrelationData.Confirm(true, null)); return null;
        }).when(rabbit).send(eq("error-ex"), eq("failed"), any(), any(CorrelationData.class));
        var recovery = new DanmakuFailureRecoverer(rabbit, "error-ex", "failed", 1000);
        var message = new org.springframework.amqp.core.Message("evidence".getBytes(), new org.springframework.amqp.core.MessageProperties());
        recovery.recover(message, new RuntimeException("private details"));
        verify(rabbit).send(eq("error-ex"), eq("failed"), eq(message), any(CorrelationData.class));
        assertThat(message.getMessageProperties().getHeaders()).isEmpty();
    }
}
