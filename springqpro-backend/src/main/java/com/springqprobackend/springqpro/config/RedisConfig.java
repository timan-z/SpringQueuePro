package com.springqprobackend.springqpro.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/* RedisConfig.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
Redis integration began when the system needed:
  - refresh token storage for JWT
  - distributed locking for multi-instance safety
  - high-performance caching for task reads
RedisConfig is where the Redis connection factory and templates are created.

[CURRENT ROLE]:
Defines:
  - LettuceConnectionFactory
  - RedisTemplate<String, Object>
  - StringRedisTemplate
These beans are used for:
  - RedisTokenStore
  - RedisDistributedLock
  - TaskRedisRepository
[FUTURE WORK]:
CloudQueue may:
  - adopt RedissonClient
  - use Redis Cluster mode
  - enable connection pooling and pipelining
--------------------------------------------------------------------------------------------------
*/

// 2025-11-21-REDIS-PHASE-NOTE: THIS FILE CREATES CONNECTION FACTORY AND REDIS TEMPLATE W/ JACKSON SERIALIZER:
@Profile("!test")
@Configuration
public class RedisConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port}") int port,
            @Value("${spring.redis.password}") String password,
            @Value("${spring.redis.ssl}") boolean ssl
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setPassword(RedisPassword.of(password));
        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .useSsl()     // NOTE: REQUIRED for Railway Redis
                        .build();
        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Use Jackson JSON serializer for values -- keeps Task metadata readable and avoids Java native serialization pitfalls.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer jackson = new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jackson);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jackson);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
