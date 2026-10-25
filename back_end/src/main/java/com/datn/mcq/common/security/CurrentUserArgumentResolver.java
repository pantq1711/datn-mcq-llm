package com.datn.mcq.common.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Tạo {@link CurrentUser} từ JWT đã xác thực; không có thì ném lỗi xác thực (ApiExceptionHandler trả 401). */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return current();
    }

    public static CurrentUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Chưa đăng nhập");
        }
        Jwt jwt = token.getToken();
        try {
            return new CurrentUser(Integer.valueOf(jwt.getSubject()), jwt.getClaimAsString("role"));
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Token không hợp lệ");
        }
    }
}
