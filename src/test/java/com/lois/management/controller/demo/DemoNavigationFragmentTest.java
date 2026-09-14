package com.lois.management.controller.demo;

import com.lois.management.auth.EmployeeUserDetailsService;
import com.lois.management.auth.JwtTokenProvider;
import com.lois.management.auth.LoginSuccessHandler;
import com.lois.management.controller.MainController;
import com.lois.management.service.IpAccessService;
import com.lois.management.service.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        MainController.class,
        DemoController.class,
        DemoStockRequestController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
        DemoDataService.class
})
@WithAnonymousUser
class DemoNavigationFragmentTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    EmployeeUserDetailsService employeeUserDetailsService;

    @MockitoBean
    LoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    IpAccessService ipAccessService;

    @Test
    void rootNavigationUsesOperationalLinks() throws Exception {
        mockMvc.perform(get("/").with(anonymous()).requestAttr("_csrf", csrfToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"/\">")))
                .andExpect(content().string(containsString("location.href=&#39;/items&#39;")))
                .andExpect(content().string(containsString("로이스 구경하기")))
                .andExpect(content().string(containsString("location.href=&#39;/demo&#39;")));
    }

    @Test
    void demoRootNavigationUsesDemoLinks() throws Exception {
        mockMvc.perform(get("/demo").with(anonymous()).requestAttr("_csrf", csrfToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"/demo\">")))
                .andExpect(content().string(containsString("location.href=&#39;/demo/items&#39;")))
                .andExpect(content().string(containsString("로이스 메인으로 가기")))
                .andExpect(content().string(containsString("location.href=&#39;/&#39;")));
    }

    @Test
    void demoSubPathNavigationUsesDemoLinks() throws Exception {
        mockMvc.perform(get("/demo/stock-requests").with(anonymous()).requestAttr("_csrf", csrfToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"/demo\">")))
                .andExpect(content().string(containsString("location.href=&#39;/demo/items&#39;")))
                .andExpect(content().string(containsString("로이스 메인으로 가기")))
                .andExpect(content().string(containsString("location.href=&#39;/&#39;")));
    }

    private DefaultCsrfToken csrfToken() {
        return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token");
    }
}
