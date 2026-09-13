package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("评价不存在或已被删除");
        }
        queryBlog(blog);
        //查询用户是否点赞
        isLiked(blog);
        return Result.ok(blog);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlog(blog);
            //查询用户是否点赞
            isLiked(blog);
        });


        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询最早点赞的前五个人
        //先到redis中查询
        String key = BLOG_LIKED_KEY+id;
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(userIds==null|| userIds.isEmpty()){
            return Result.ok(new ArrayList<>());
        }
        //转换为list
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
        //用来作为order by field的排序规则
        String idsStr = StrUtil.join(",",ids);

        List<UserDTO> userDTOs = userService.query().in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //再到数据库具体查询
        return Result.ok(userDTOs);
    }

    @Override
    public Result saveBlog(Blog blog) {

        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 2.保存探店博文
        save(blog);

        // 3.查询笔记作者的所有粉丝select * from follow where follow_user_id=userId
        List<Follow> follows = followService.query().eq("follow_user_id",userId).list();


        // 4.推送笔记id给所有粉丝
        //使用sorted_set存储笔记id,key用FEED_KEY+userId
        for (Follow follow : follows) {
            String key= FEED_KEY+follow.getUserId();

            //推送数据
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }



        // 返回id
        return Result.ok(blog.getId());
    }
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 2. 查询收件箱
        // 2.1当前用户的key
        //2.2 redis sorted-set查询
        String key = FEED_KEY+user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        // 判断是否为空
        if(typedTuples==null ||typedTuples.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        // 3. 解析数据: blogId、minTime (时间戳)、offset
        List<Long> ids= new ArrayList<>(typedTuples.size());
        int os=1;
        long minTime=0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //3.1获取id
            Long id = Long.valueOf(typedTuple.getValue());
            ids.add(id);
            //3.2获取时间戳
            long time= typedTuple.getScore().longValue();
            if(minTime==time){
                os++;
            }else{
                minTime=time;
                os=0;
            }
        }

        // 4. 根据id查询blog,数据库查询时（条件in）不会按顺序
        String idsStr=StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idsStr + ")").list();

        for (Blog blog : blogs) {
            //5.1 查询发布该blog的用户信息
            queryBlog(blog);
            //5.2 查询当前用户是否给该blog点过赞
            isLiked(blog);
        }
        // 5. 封装并返回
        ScrollResult scrollResult=new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    private void queryBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result likeBlog(Long id) {

        //获取userId
        Long userId = UserHolder.getUser().getId();
        //一人一赞，
        //如果没有点赞，赞+1，把userId存到set集合中
        String key = BLOG_LIKED_KEY + id;
        //Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //想要实现点赞排行榜（通过sorted_set,score用时间戳）
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
       /* if(Boolean.FALSE.equals(isLiked)){
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }

        }else{
            //如果点赞了，赞-1,把userId从set集合中删除
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }

        }*/
        if (score == null) {
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        } else {
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }


        return Result.ok();
    }

    public void isLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        //如果没登陆，直接返回
        if (user == null) {
            return;
        }
        //查询用户user是否给博客id点赞
        //user
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        /*Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(Boolean.TRUE.equals(isLiked));*/
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
