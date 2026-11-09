package com.lois.management.domain.ipaccess;


import com.lois.management.config.filter.IpWhitelistFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IpWhitelistProperties.class)
@RequiredArgsConstructor
public class IpWhitelistConfig {

    private final IpAccessService ipAccessService;

//    @Bean
//    public FilterRegistrationBean<IpWhitelistFilter> ipWhitelistFilter(IpWhitelistProperties properties) {
//        FilterRegistrationBean<IpWhitelistFilter> registrationBean = new FilterRegistrationBean<>();
//        registrationBean.setFilter(new IpWhitelistFilter(properties, ipAccessService));
//        registrationBean.addUrlPatterns("/*");
//        registrationBean.setOrder(1);
//        return registrationBean;
//    }

    @Bean
    public IpWhitelistFilter ipWhitelistFilter(IpWhitelistProperties properties) {
        return new IpWhitelistFilter(properties, ipAccessService);
    }
}
