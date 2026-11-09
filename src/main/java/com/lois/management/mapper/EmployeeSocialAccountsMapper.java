package com.lois.management.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeSocialAccountsMapper {
    void insert(@Param("employeeId") Long employeeId,
                        @Param("provider") String provider,
                        @Param("providerUserId") String providerUserId,
                @Param("email") String email);

    Long findByProviderNProviderUserId(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId);
}
