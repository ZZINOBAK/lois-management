package com.lois.management.config;


import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.service.IpAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IpWhitelistProperties.class)
@RequiredArgsConstructor
public class IpWhitelistConfig {

    private final IpAccessService ipAccessService;

    @Bean
    public FilterRegistrationBean<IpWhitelistFilter> ipWhitelistFilter(IpWhitelistProperties properties) {
        FilterRegistrationBean<IpWhitelistFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new IpWhitelistFilter(properties, ipAccessService));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
