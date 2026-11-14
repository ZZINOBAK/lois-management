package com.lois.management.global.config;

import com.lois.management.global.sse.SseNotifyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final SseNotifyInterceptor sseNotifyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sseNotifyInterceptor)
                .addPathPatterns(SseNotifyInterceptor.PATH_PATTERNS)
                .excludePathPatterns(SseNotifyInterceptor.EXCLUDE_PATH_PATTERNS);
    }
}
