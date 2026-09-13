package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

        //获取用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follow:"+userId;
        //关注
        if (Boolean.TRUE.equals(isFollow)) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            boolean success = save(follow);

            if(success){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        } else {
            //取关
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("follow_user_id", followUserId).eq("user_id", userId);
            boolean success = remove(queryWrapper);
            if(success){
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long followUserId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count>0);
    }

    @Override
    public Result getCommonFollow(Long id) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follow:"+userId;
        String key2 = "follow:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        // todo缓存没命中怎么办
        if(intersect==null|| intersect.isEmpty()){
            //无交集，返回空集合
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据共同关注的id查找信息
        List<UserDTO> userDTOS=userService
                .listByIds(ids)
                .stream()
                .map(user-> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
