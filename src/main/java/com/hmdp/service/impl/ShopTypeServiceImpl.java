package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 服务实现类
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺
     * 用redis的String类型存储
     * @return
     */
    public Result queryList() {
        //1.从redis中查询所有商铺缓存
        log.info("从redis中查询所有商铺的缓存...");
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        //2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            //存在，转成java对象并返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //4.不存在，查询数据库，使用mybatisplus的方法快速查询
        log.info("从数据库中查询所有商铺...");
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //数据库若不存在，返回错误
        if (shopTypeList == null) {
            return Result.fail("不存在店铺");
        }
        //5.存在，将数据写入redis，并返回
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList), RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypeList);
    }

    /**
     * 查询所有商铺
     * 用redis的list类型存储
     * @return
     */
//    public Result queryList() {
//        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
//        if (shopTypeJson != null && !shopTypeJson.isEmpty()) {
//            List<ShopType> shopTypes = shopTypeJson.stream()
//                    .map(shopType -> JSONUtil.toBean(shopType, ShopType.class))
//                    .collect(Collectors.toList());
//            return Result.ok(shopTypes);
//        }
//        List<ShopType> shopTypes = query().orderByAsc("sort").list();
//        if(shopTypes==null||shopTypes.isEmpty()){
//            return Result.fail("没有分类数据");
//        }
//        for (ShopType shopType : shopTypes) {
//            String json = JSONUtil.toJsonStr(shopType);
//            stringRedisTemplate.opsForList().rightPush(RedisConstants.CACHE_SHOP_TYPE_KEY,json);
//        }
//        return Result.ok(shopTypes);
//    }
}
