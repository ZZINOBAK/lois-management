package com.lois.management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "lois.ip-whitelist")
public class IpWhitelistProperties {
    private boolean enabled;
    private List<String> allowed;

    // 새로 추가
    private List<String> publicPaths;
}
