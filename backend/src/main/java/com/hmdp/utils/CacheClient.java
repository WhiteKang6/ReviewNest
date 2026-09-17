package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {



    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //缓存穿透查询
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.命中，返回商铺信息
        if(StrUtil.isNotBlank(json)){
            R r = JSONUtil.toBean(json,type);
            return r;
        }
        if(json != null){
            //空值
            return null;
        }
        //3.未命中，从数据库中查询商铺信息
        R r = dbFallback.apply(id);
        //4.判断商铺是否存在
        if(r == null){
            //将null写入redis
            //stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //6.如果不存在，返回失败结果
            return null;
        }
        //5.如果存在，将商铺信息缓存到redis中
        set(key,r,time,unit);

        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑查询商铺信息
    public <R,ID> R queryWithLogic(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis中查询商铺缓存
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        //2.未命中，返回空
        if(StrUtil.isBlank(redisDataJson)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject jsonObject = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(jsonObject,type);

        LocalDateTime expireTime = redisData.getExpireTime();
        //3.命中，判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.未过期，返回商铺信息
            return r;
        }

        //5.过期，获取互斥锁，判断是否获取到
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //7.有，开启独立线程
            //7.1.再次查询缓存
//            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
//            if(StrUtil.isNotBlank(shopJson2)){
//                //命中，返回商铺信息
//                RedisData redisData2 = JSONUtil.toBean(shopJson2, RedisData.class);
//                JSONObject jsonObject2 = (JSONObject)redisData2.getData();
//                Shop shop2 = JSONUtil.toBean(jsonObject2,Shop.class);
//                return shop2;
//            }

            //7.2.独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查数据库库
                    R r2 = dbFallback.apply(id);
                    //缓存到redis
                    setWithLogicExpire(key,r2,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }
        //6.没有，直接返回商铺信息
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
