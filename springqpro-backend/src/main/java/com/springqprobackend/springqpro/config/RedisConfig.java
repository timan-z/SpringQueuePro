package com.springqprobackend.springqpro.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// 2025-11-21-REDIS-PHASE-NOTE: THIS FILE CREATES CONNECTION FACTORY AND REDIS TEMPLATE W/ JACKSON SERIALIZER:
@Configuration
public class RedisConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port") int port
    ) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(cfg);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Use Jackson JSON serializer for values -- keeps Task metadata readable and avoids Java native serialization pitfalls.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        GenericJackson2JsonRedisSerializer jackson = new GenericJackson2JsonRedisSerializer(mapper);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jackson);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jackson);
        template.afterPropertiesSet();
        return template;
    }
}
