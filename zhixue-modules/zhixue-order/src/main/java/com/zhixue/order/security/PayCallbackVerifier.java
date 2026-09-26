package com.zhixue.order.security;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 支付回调校验器。
 *
 * <p>回调接口必须公网可达，无法用登录态保护，因此改用共享密钥签名：
 * 只有能算出正确 HMAC 的调用方才被信任。同时核对回调金额与订单金额，
 * 防止「签名合法但少付」的情况。</p>
 *
 * <p>当前为 HMAC-SHA256 实现，适配尚未接入真实支付 SDK 的阶段。
 * 后续接入支付宝/微信时，把 {@link #verify} 内的签名校验替换为
 * 渠道各自的 RSA2 + 平台公钥验签即可，调用方无需改动。</p>
 */
@Slf4j
@Component
public class PayCallbackVerifier {

    @Value("${order.pay-callback-secret:}")
    private String callbackSecret;

    /**
     * 校验回调的签名与金额。校验不通过直接抛异常，调用方不得捕获后放行。
     *
     * @param message     第三方回调内容
     * @param orderAmount 订单在本地记录的应付金额
     */
    public void verify(PayResultMessage message, BigDecimal orderAmount) {
        if (!StringUtils.hasText(callbackSecret)) {
            // 宁可拒绝全部回调，也不能在未配置密钥时无条件放行
            throw new ServiceException("支付回调密钥未配置，拒绝处理回调");
        }
        if (!StringUtils.hasText(message.getSign())) {
            throw new ServiceException("支付回调缺少签名");
        }
        if (!StringUtils.hasText(message.getPayNo())) {
            throw new ServiceException("支付回调缺少第三方流水号");
        }
        if (message.getAmount() == null) {
            throw new ServiceException("支付回调缺少支付金额");
        }

        String expected = sign(message);
        if (!constantTimeEquals(expected, message.getSign())) {
            log.warn("支付回调签名校验失败 orderNo={}, payChannel={}",
                    message.getOrderNo(), message.getPayChannel());
            throw new ServiceException("支付回调签名校验失败");
        }

        if (orderAmount == null || message.getAmount().compareTo(orderAmount) != 0) {
            log.warn("支付回调金额与订单金额不一致 orderNo={}, callbackAmount={}, orderAmount={}",
                    message.getOrderNo(), message.getAmount(), orderAmount);
            throw new ServiceException("支付回调金额与订单金额不一致");
        }
    }

    /**
     * 计算回调签名。参与签名的字段固定顺序拼接，任一字段被篡改都会导致签名不匹配。
     * 金额统一按 stripTrailingZeros 归一化，避免 999.00 与 999 产生不同签名。
     */
    public String sign(PayResultMessage message) {
        if (!StringUtils.hasText(callbackSecret)) {
            throw new ServiceException("支付回调密钥未配置，无法生成签名");
        }
        String payload = "orderNo=" + nullToEmpty(message.getOrderNo())
                + "&payChannel=" + nullToEmpty(message.getPayChannel())
                + "&payNo=" + nullToEmpty(message.getPayNo())
                + "&payStatus=" + (message.getPayStatus() == null ? "" : message.getPayStatus())
                + "&amount=" + normalizeAmount(message.getAmount());
        return hmacSha256(payload);
    }

    private String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ServiceException("支付回调签名计算失败");
        }
    }

    /** 定长比较，避免通过响应耗时差异逐字节猜测签名。 */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
