package com.lois.management.auth;

import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.controller.demo.DemoController;
import com.lois.management.service.demo.DemoDataService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import({
        SecurityConfig.class,
        DemoDataService.class,
        DemoSecurityConfigTest.PassThroughIpWhitelistFilterConfig.class
})
class DemoSecurityConfigTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    EmployeeUserDetailsService employeeUserDetailsService;

    @MockitoBean
    LoginSuccessHandler loginSuccessHandler;

    @Test
    void demoIsPublicButOperationalPagesStillRequireLogin() throws Exception {
        mockMvc.perform(get("/demo"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/stock-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @TestConfiguration
    static class PassThroughIpWhitelistFilterConfig {
        @Bean
        IpWhitelistFilter ipWhitelistFilter() {
            return new IpWhitelistFilter(null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain filterChain)
                        throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
