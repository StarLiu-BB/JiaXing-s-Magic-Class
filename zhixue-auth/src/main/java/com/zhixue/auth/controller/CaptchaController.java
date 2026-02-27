package com.zhixue.auth.controller;

import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器
 * 作用：这个类提供图形验证码的生成接口。
 * 用户登录时需要输入验证码，防止机器人自动登录。
 * 验证码是一张包含随机字符的图片，用户需要识别图片中的字符并输入。
 */
@RestController
@RequiredArgsConstructor
public class CaptchaController {

    private final StringRedisTemplate redisTemplate;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;

    @GetMapping("/captcha")
    public R<Map<String, String>> getCaptcha() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = generateCode();
        
        // 存入 Redis，5分钟过期
        String key = CacheConstants.CAPTCHA_KEY + uuid;
        redisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
        
        // 生成图片
        String base64 = generateImage(code);
        
        Map<String, String> result = new HashMap<>();
        result.put("uuid", uuid);
        result.put("img", "data:image/png;base64," + base64);
        
        return R.ok(result);
    }

    private String generateCode() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Random random = new Random();

        // 背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 验证码
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
