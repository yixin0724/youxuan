package com.hmdp.handler;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @author yixin
 * @date 2025/6/8
 * @description 登录验证拦截器
 * 写完拦截器后记得要注册
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 前置拦截器
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            //没有用户，拦截，返回401
            response.setStatus(401);
            return false;
        }
        //2.有用户，放行
        return true;
    }
}
