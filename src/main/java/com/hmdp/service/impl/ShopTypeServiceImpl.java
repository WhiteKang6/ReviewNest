package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = "cache:shopTypeList";
        //查缓存
        String typeListStr = stringRedisTemplate.opsForValue().get(key);
        //存在
        if(StrUtil.isNotBlank(typeListStr)){
            List<ShopType> typeList = JSONUtil.toList(typeListStr, ShopType.class);
        }
        //不存在,查数据库
        List<ShopType> typeList = list();
        //不存在
        if(typeList ==null || typeList.size()==0){
            return Result.fail("查询店铺类型失败");
        }
        //存在,将数据库中的数据缓存到redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));
        //返回结果
        return Result.ok(typeList);
    }
}
