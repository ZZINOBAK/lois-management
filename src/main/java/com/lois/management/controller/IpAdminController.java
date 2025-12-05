package com.lois.management.controller;

import com.lois.management.domain.AllowedIp;
import com.lois.management.mapper.AllowedIpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/ip-access")
public class IpAdminController {
    private final AllowedIpMapper allowedIpMapper;

    @GetMapping
    public String listPending(Model model) {
        List<AllowedIp> pendingList = allowedIpMapper.findByStatus("PENDING");
        model.addAttribute("pendingList", pendingList);
        return "ipaccess/admin-list";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        allowedIpMapper.updateStatus(id, "APPROVED");
        return "redirect:/admin/ip-access";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable("id") Long id) {
        allowedIpMapper.updateStatus(id, "REJECTED");
        return "redirect:/admin/ip-access";
    }
}
