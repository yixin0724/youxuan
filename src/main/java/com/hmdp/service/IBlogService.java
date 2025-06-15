package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博文
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询最新博文
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询点赞用户
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博文
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询用户关注用户所发布的博客
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
