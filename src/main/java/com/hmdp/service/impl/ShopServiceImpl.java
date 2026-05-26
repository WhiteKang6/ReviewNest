package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate  stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient
//                .queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogic(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //缓存互斥锁查询
//        Shop shop = queryWithMutex(id);
//        if(shop == null){
//            return Result.fail("商铺不存在");
//        }

        //逻辑查询商铺信息
        //Shop shop = queryWithLogic(id);
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑查询商铺信息
    public Shop queryWithLogic(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis中查询商铺缓存
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        //2.未命中，返回空
        if(StrUtil.isBlank(redisDataJson)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject jsonObject = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject,Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();
        //3.命中，判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.未过期，返回商铺信息
            return shop;
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
                    this.savaShopToRedisData(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }
        //6.没有，直接返回商铺信息
        return shop;
    }
    //缓存互斥锁查询
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.命中，返回商铺信息
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson != null){
            //空值
            return null;
        }
        //3.未命中，从数据库中查询商铺信息
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            //3.1.获取互斥锁
            if(!tryLock(lockKey)){
                //失败，休眠，重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功获取锁
            //再次查询缓存
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJson2)){
                //命中，返回商铺信息
                return JSONUtil.toBean(shopJson2, Shop.class);
            }
            shop = getById(id);
            //模拟数据库查询时间，避免查询过快
            Thread.sleep(100);
            //4.判断商铺是否存在
            if(shop == null){
                //将null写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //6.如果不存在，返回失败结果
                return null;
            }
            //5.如果存在，将商铺信息缓存到redis中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally{
            //6.释放锁
            unlock(lockKey);
        }

        return shop;
    }
    //缓存穿透查询
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.命中，返回商铺信息
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson != null){
            //空值
            return null;
        }
        //3.未命中，从数据库中查询商铺信息
        Shop shop = getById(id);
        //4.判断商铺是否存在
        if(shop == null){
            //将null写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //6.如果不存在，返回失败结果
            return null;
        }
        //5.如果存在，将商铺信息缓存到redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺id不能为空");
        }
        //先修改数据库，在删除缓存
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
    public void savaShopToRedisData(Long id,Long expireSeconds) throws InterruptedException {
        //查询数据库
        Shop shop = getById(id);
        //模拟数据库查询时间，避免查询过快
        Thread.sleep(200);
        //创建RedisData对象
        RedisData redisData= new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
