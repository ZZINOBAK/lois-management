package com.lois.management.controller;

import com.lois.management.dto.ipaccess.AccessRequestForm;
import com.lois.management.service.IpAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AccessRequestController {
    private final IpAccessService ipAccessService;

    @GetMapping("/access-request")
    public String showAccessRequestForm(@RequestParam("ip")  String ip, Model model) {
        AccessRequestForm form = new AccessRequestForm();
        form.setIpAddress(ip);
        model.addAttribute("form", form);
        return "ipaccess/form";
    }

    @PostMapping("/access-request")
    public String submitAccessRequest(AccessRequestForm form, Model model) {

        ipAccessService.savePendingRequest(
                form.getIpAddress(),
                form.getName(),
                form.getContact(),
                form.getMemo()
        );

        return "ipaccess/complete"; // "요청이 접수되었습니다" 페이지
    }
}
