package com.ridehub.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridehub.common.redis.idempotency.RedisIdempotencyManager;
import com.ridehub.common.redis.lock.DistributedLockAspect;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration
public class RidehubRedisAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisConnectionFactory.class)
    public static class RedisTemplateConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "redisTemplate")
        public RedisTemplate<String, Object> redisTemplate(
                RedisConnectionFactory connectionFactory,
                @Autowired(required = false) ObjectMapper objectMapper) {

            ObjectMapper mapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);
            StringRedisSerializer stringSerializer = new StringRedisSerializer();

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(stringSerializer);
            template.setValueSerializer(jsonSerializer);
            template.setHashKeySerializer(stringSerializer);
            template.setHashValueSerializer(jsonSerializer);
            template.afterPropertiesSet();
            return template;
        }

        @Bean
        @ConditionalOnMissingBean
        public RedisIdempotencyManager redisIdempotencyManager(StringRedisTemplate stringRedisTemplate) {
            return new RedisIdempotencyManager(stringRedisTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedissonClient.class)
    public static class RedissonLockConfiguration {

        @Bean
        @ConditionalOnBean(RedissonClient.class)
        @ConditionalOnMissingBean
        public DistributedLockAspect distributedLockAspect(RedissonClient redissonClient) {
            return new DistributedLockAspect(redissonClient);
        }
    }
}
