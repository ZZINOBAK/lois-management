package com.lois.management.service;

import com.lois.management.domain.AllowedIp;
import com.lois.management.mapper.AllowedIpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IpAccessService {
    private final AllowedIpMapper allowedIpMapper;

    public boolean isApproved(String ip) {
        return allowedIpMapper.countApprovedByIp(ip) > 0;
    }

    public void savePendingRequest(String ip, String name, String contact, String memo) {

        // 이미 PENDING이면 또 안 만들고 그냥 무시 (선택 로직)
        if (allowedIpMapper.countPendingByIp(ip) > 0) {
            return;
        }

        AllowedIp entity = new AllowedIp();
        entity.setIpAddress(ip);
        entity.setName(name);
        entity.setContact(contact);
        entity.setMemo(memo);
        entity.setStatus("PENDING");

        allowedIpMapper.insert(entity);
    }
}
