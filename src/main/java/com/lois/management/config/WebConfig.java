package com.lois.management.config;

import com.lois.management.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
//    private final JwtAuthFilter jwtAuthFilter;

//    @Bean
//    public FilterRegistrationBean<JwtAuthFilter> jwtFilter() {
//        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
//        bean.setFilter(jwtAuthFilter);
//        bean.addUrlPatterns("/api/*");
//        return bean;
//    }
}
