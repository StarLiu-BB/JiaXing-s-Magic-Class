package com.zhixue.common.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * 防止雪花 ID 在前端丢失精度。
 *
 * <p>实体主键为 19 位雪花 ID，超过 JavaScript 安全整数（2^53-1）。
 * 实测后端返回 {@code 2104269046074900481}，前端解析为
 * {@code 2104269046074900500}，再用它编辑/删除时后端返回"不存在"——
 * 通过管理后台新建的任何记录都无法再被修改。</p>
 *
 * <p>设计取舍：<b>只把超出安全范围的值输出为字符串</b>，其余仍为数字。
 * 若对所有 Long 一刀切转字符串，{@code total}、计数等字段会从数字变成字符串，
 * 破坏前端当前正常工作的分页与计算逻辑。</p>
 *
 * <p>以 {@link Jackson2ObjectMapperBuilderCustomizer} 方式在 Spring Boot
 * 默认 ObjectMapper 上叠加，而非替换它，确保 JavaTimeModule 等默认能力保留。</p>
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
public class JacksonLongPrecisionConfig {

    /** JavaScript Number.MAX_SAFE_INTEGER。 */
    static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longPrecisionCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("LongPrecisionModule");
            SafeLongSerializer serializer = new SafeLongSerializer();
            module.addSerializer(Long.class, serializer);
            module.addSerializer(Long.TYPE, serializer);
            builder.modulesToInstall(module);
        };
    }

    /**
     * 安全范围内输出数字，超出则输出字符串。
     * 反序列化无需定制：Jackson 默认即可把 "2104..." 字符串还原为 Long。
     */
    static final class SafeLongSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}
