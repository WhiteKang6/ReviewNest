package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP=1640995200;
    private static final int COUNT_BITS=32;

    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    public long nextId(String keyPrefix){
        //相对于BEGIN_TIMESTAMP的时间戳
        LocalDateTime now=LocalDateTime.now();
        Long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1.获取当前日期，精准到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        //3.拼接并返回
        return timestamp<<COUNT_BITS|count;

    }
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        System.out.println(time);
        Long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
        LocalDateTime now=LocalDateTime.now();
        System.out.println(now);
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        System.out.println(date);
    }
}
