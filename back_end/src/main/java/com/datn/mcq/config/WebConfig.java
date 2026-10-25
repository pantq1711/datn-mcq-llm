package com.datn.mcq.config;

import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.common.security.CurrentUserArgumentResolver;
import com.datn.mcq.enums.DbEnum;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new DbEnumWebConverterFactory());
    }

    /** W1-1: controller nhận {@link CurrentUser} (người gọi lấy từ JWT). CORS cấu hình ở SecurityConfig. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class DbEnumWebConverterFactory
            implements ConverterFactory<String, DbEnum>, ConditionalConverter {

        @Override
        public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
            Class<?> type = targetType.getType();
            return type.isEnum() && DbEnum.class.isAssignableFrom(type);
        }

        @Override
        public <T extends DbEnum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> (T) DbEnum.fromDbValue((Class) targetType, source);
        }
    }
}
