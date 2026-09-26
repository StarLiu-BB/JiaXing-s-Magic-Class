package com.zhixue.order.security;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付回调验签测试。
 *
 * <p>覆盖阶段 1 验收标准：未签名/错签名/金额篡改的回调必须被拒绝，
 * 只有携带正确 HMAC 签名且金额一致的回调才能通过。</p>
 */
class PayCallbackVerifierTest {

    private static final String SECRET = "test-pay-callback-secret-at-least-32-bytes";

    private PayCallbackVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new PayCallbackVerifier();
        ReflectionTestUtils.setField(verifier, "callbackSecret", SECRET);
    }

    private PayResultMessage message(String orderNo, String payNo, BigDecimal amount) {
        PayResultMessage msg = new PayResultMessage();
        msg.setOrderNo(orderNo);
        msg.setPayChannel("alipay");
        msg.setPayNo(payNo);
        msg.setPayStatus(1);
        msg.setAmount(amount);
        return msg;
    }

    @Test
    void shouldRejectCallbackWithoutSignature() {
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999.00"));

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名");
    }

    @Test
    void shouldRejectCallbackWithWrongSignature() {
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999.00"));
        msg.setSign("deadbeef");

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名");
    }

    @Test
    void shouldAcceptCallbackWithValidSignature() {
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999.00"));
        msg.setSign(verifier.sign(msg));

        assertThatCode(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenAmountTamperedAfterSigning() {
        // 攻击者拿到合法签名后篡改金额：签名校验就应失败
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999.00"));
        msg.setSign(verifier.sign(msg));
        msg.setAmount(new BigDecimal("0.01"));

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名");
    }

    @Test
    void shouldRejectWhenCallbackAmountDiffersFromOrderAmount() {
        // 签名合法，但回调金额与订单真实金额不一致（少付）
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("0.01"));
        msg.setSign(verifier.sign(msg));

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("金额");
    }

    @Test
    void shouldTreatEquivalentAmountScalesAsEqual() {
        // 999.00 与 999 应视为同一金额，避免误拒真实回调
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999"));
        msg.setSign(verifier.sign(msg));

        assertThatCode(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenPayNoMissing() {
        // 缺少第三方流水号时不得放行，更不得自造流水号
        PayResultMessage msg = message("ORD001", null, new BigDecimal("999.00"));
        msg.setSign(verifier.sign(msg));

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("流水号");
    }

    @Test
    void shouldRejectWhenCallbackAmountMissing() {
        PayResultMessage msg = message("ORD001", "PAY001", null);
        msg.setSign(verifier.sign(msg));

        assertThatThrownBy(() -> verifier.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("金额");
    }

    @Test
    void shouldFailFastWhenSecretNotConfigured() {
        PayCallbackVerifier unconfigured = new PayCallbackVerifier();
        ReflectionTestUtils.setField(unconfigured, "callbackSecret", "");
        PayResultMessage msg = message("ORD001", "PAY001", new BigDecimal("999.00"));
        msg.setSign("whatever");

        assertThatThrownBy(() -> unconfigured.verify(msg, new BigDecimal("999.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void signatureShouldBeStableForSameInput() {
        PayResultMessage a = message("ORD001", "PAY001", new BigDecimal("999.00"));
        PayResultMessage b = message("ORD001", "PAY001", new BigDecimal("999.00"));

        assertThatCode(() -> {
            String signA = verifier.sign(a);
            String signB = verifier.sign(b);
            if (!signA.equals(signB)) {
                throw new AssertionError("相同输入必须产生相同签名");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void differentOrderShouldProduceDifferentSignature() {
        PayResultMessage a = message("ORD001", "PAY001", new BigDecimal("999.00"));
        PayResultMessage b = message("ORD002", "PAY001", new BigDecimal("999.00"));

        assertThatCode(() -> {
            if (verifier.sign(a).equals(verifier.sign(b))) {
                throw new AssertionError("不同订单号必须产生不同签名");
            }
        }).doesNotThrowAnyException();
    }
}
