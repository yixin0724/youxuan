package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号，使用正则表达式工具类进行判断
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误
            return Result.fail("手机号格式错误！");
        }
        //3.如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis中,过期时间2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //TODO 发送验证码
        //5.发送验证码，可以使用阿里云的短信服务，这里先设置假的模拟
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    /**
     * 登录功能
     * 基于session的登录不需要把user的信息都存进去，因为session是基于内存的，信息越少越好。
     * 基于session的登录不需要返回登录凭证，因为用户信息已经保存在session中
     * 而session是基于cookie，有自己的唯一sessionId，在访问tomcat的时候，以及把sessionId放到cookie中了，而访问如果找到了session则说明登录成功
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号，使用正则表达式工具类进行判断
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        //2.校验验证码
        //获取前端提交的验证码
        String code = loginForm.getCode();
        //从redis中获取生成的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }
        //3.根据手机号查询用户
        //query()是继承ServiceImpl后，   mybatis-plus提供的方法，查询条件为phone，查询结果为User
        User user = query().eq("phone", phone).one();
        //4.判断用户是否存在，若不存在，则创建新用户保存到数据库
        if (user == null) {
            //创建并保存新用户
            user = createUserWithPhone(phone);
        }
        //5.保存用户信息到redis中（不管用户存在不存在都要做）
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将UserDTO对象转为HashMap存储，因为用的String序列化器，所以要把key和value都转为String
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //以token加前缀为键存储到redis中
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //返回token
        return Result.ok(token);
    }

    /**
     * 签到功能
     * @return
     */
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 统计连续签到
     * @return
     */
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止所有的签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (result == null || result.isEmpty()) {
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == 0 || num == null) {
            return Result.ok(0);
        }
        //6.循坏遍历
        int count = 0;
        while (true) {
            //让这个数字与1做与运算，得到数字的最后一个bit位，判断这个bit是否为0
            if ((num & 1) == 0) {
                //如果为0，未签到
                break;
            } else {
                //如果不为0，已签到，计算器+1
                count++;
                //把数字右移一位，抛弃最后一个bit位，继续下一个bit位
                num >>>= 1;
            }
        }
        return Result.ok(count);

    }

    //新增用户
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }

    /**
     * 退出登录
     * @return
     */
    public Result logout() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.删除token
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + userId);
        //3.返回
        return Result.ok();
    }
}
