package com.lois.management.mapper;

import com.lois.management.domain.AllowedIp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AllowedIpMapper {
    // 해당 IP가 APPROVED 인지 여부 체크용
    int countApprovedByIp(@Param("ip") String ip);

    // 접근 요청 저장(PENDING)
    void insert(AllowedIp allowedIp);

    // PENDING 목록 조회 (관리자 화면용)
    List<AllowedIp> findByStatus(@Param("status") String status);

    // 상태 변경 (APPROVED/REJECTED)
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    // 이미 PENDING 있는지 확인 (중복 요청 방지용, 선택)
    int countPendingByIp(@Param("ip") String ip);
}
