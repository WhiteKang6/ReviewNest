package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;
    @Test
    void testSaveShop() throws InterruptedException {
        //shopService.savaShopToRedisData(1L,10L);
        cacheClient.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,"redis",5L, TimeUnit.SECONDS);
    }
}
