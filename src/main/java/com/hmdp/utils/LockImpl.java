package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class LockImpl implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public LockImpl(String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(Long timeoutSec) {
        String threadId =ID_PREFIX + Thread.currentThread().getId();

        //设置锁
        Boolean b = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX +name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }
    @Override
    public void unlock() {

       //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );

    }

    /*@Override
    public void unlock() {
        //判断是不是自己的锁
        String threadId =ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(prefix + name);
        if(id.equals(threadId)){
            //释放锁
            stringRedisTemplate.delete(prefix+name);
        }

    }*/


}
