package com.lois.management.controller.demo;

import com.lois.management.auth.EmployeeUserDetailsService;
import com.lois.management.auth.JwtTokenProvider;
import com.lois.management.auth.LoginSuccessHandler;
import com.lois.management.service.IpAccessService;
import com.lois.management.service.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({
        DemoController.class,
        DemoItemController.class,
        DemoStockRequestController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
        DemoDataService.class
})
@WithAnonymousUser
class DemoItemControllerTest {
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
    void demoItemsDashboardIsPublicAndRendered() throws Exception {
        mockMvc.perform(get("/demo/items").requestAttr("_csrf", csrfToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/item/dashboard"))
                .andExpect(content().string(containsString("상품 등록 Demo")))
                .andExpect(content().string(containsString("hx-get=\"/demo/items/grid?category=전체\"")))
                .andExpect(content().string(containsString("action=\"/demo/items\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void demoItemCanBeCreatedWithCsrfAndSeenInSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/demo/items")
                        .session(session)
                        .param("_csrf", "test-token")
                        .param("categoryId", "2")
                        .param("itemName", "데모 레몬티"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/items"));

        mockMvc.perform(get("/demo/items").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("데모 레몬티")));
    }

    @Test
    void itemManagementGridUsesSeparatedReadOnlyFragment() throws Exception {
        mockMvc.perform(get("/demo/items/grid").param("category", "음료"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("딸기 시럽")))
                .andExpect(content().string(not(containsString("POST /demo/stock-requests"))))
                .andExpect(content().string(not(containsString("hx-post=\"/demo/stock-requests\""))))
                .andExpect(content().string(not(containsString("action=\"/demo/stock-requests\""))));
    }

    @Test
    void existingStockRequestItemFragmentEndpointIsKept() throws Exception {
        mockMvc.perform(get("/demo/items").param("category", "음료"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("딸기 시럽")))
                .andExpect(content().string(containsString("hx-post=\"/demo/stock-requests\"")));
    }

    @Test
    void createdItemIsSharedWithStockRequestItemsInSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/demo/items")
                        .session(session)
                        .param("_csrf", "test-token")
                        .param("categoryId", "4")
                        .param("itemName", "데모 바닐라빈"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/items"));

        mockMvc.perform(get("/demo/items").session(session).param("category", "베이킹"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("데모 바닐라빈")))
                .andExpect(content().string(containsString("hx-post=\"/demo/stock-requests\"")));
    }

    @Test
    void invalidItemInputIsRejectedWithoutAddingItems() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/demo/items")
                        .session(session)
                        .param("_csrf", "test-token")
                        .param("categoryId", "1")
                        .param("itemName", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/items"));

        mockMvc.perform(post("/demo/items")
                        .session(session)
                        .param("_csrf", "test-token")
                        .param("categoryId", "999")
                        .param("itemName", "잘못된 카테고리 상품"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/items"));

        mockMvc.perform(post("/demo/items")
                        .session(session)
                        .param("_csrf", "test-token")
                        .param("itemName", "카테고리 없는 상품"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/items"));

        mockMvc.perform(get("/demo/items").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("잘못된 카테고리 상품"))))
                .andExpect(content().string(not(containsString("카테고리 없는 상품"))));
    }

    private DefaultCsrfToken csrfToken() {
        return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token");
    }
}
