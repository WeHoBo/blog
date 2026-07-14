package com.blog.common.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        Fastjson2RedisSerializer fastjson2Serializer = new Fastjson2RedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(fastjson2Serializer);
        template.setHashValueSerializer(fastjson2Serializer);

        template.afterPropertiesSet();
        return template;
    }

    static class Fastjson2RedisSerializer implements RedisSerializer<Object> {

        static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

        @Override
        public byte[] serialize(Object obj) throws SerializationException {
            if (obj == null) {
                return new byte[0];
            }
            return JSON.toJSONString(obj, JSONWriter.Feature.WriteClassName).getBytes(DEFAULT_CHARSET);
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return JSON.parseObject(new String(bytes, DEFAULT_CHARSET), Object.class, JSONReader.Feature.SupportAutoType);
        }
    }
}
