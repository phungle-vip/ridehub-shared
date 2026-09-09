package com.ridehub.common.redis.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);
    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String resolvedKey = resolveKey(distributedLock.key(), method, joinPoint.getArgs());
        String lockKey = distributedLock.prefix() + resolvedKey;

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        log.debug("Attempting to acquire distributed lock for key: {}", lockKey);
        try {
            acquired = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            if (!acquired) {
                log.warn("Could not acquire distributed lock for key: {} within {} {}", lockKey, distributedLock.waitTime(), distributedLock.timeUnit());
                throw new LockAcquisitionException("Unable to acquire lock for key: " + lockKey);
            }

            log.debug("Successfully acquired distributed lock for key: {}", lockKey);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock for key: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("Released distributed lock for key: {}", lockKey);
                } catch (Exception e) {
                    log.error("Failed to release distributed lock for key: {}: {}", lockKey, e.getMessage());
                }
            }
        }
    }

    private String resolveKey(String keyExpression, Method method, Object[] args) {
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        String[] paramNames = paramDiscoverer.getParameterNames(method);
        EvaluationContext context = new StandardEvaluationContext();

        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : keyExpression;
    }
}
