package com.lois.management.auth;

import com.lois.management.dto.employee.EmployeeRequest;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    @GetMapping("/after-login")
    public String afterLogin() {
        return "auth/after-login"; // templates/auth/after-login.html
    }
}
