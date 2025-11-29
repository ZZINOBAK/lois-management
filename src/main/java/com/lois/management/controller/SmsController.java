package com.lois.management.controller;

import com.lois.management.domain.StockRequest;
import com.lois.management.dto.SmsDto;
import com.lois.management.service.SmsService;
import com.lois.management.service.StockRequestService;
import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController {
    private final SmsService smsService;

    /**
     * 단일 메시지 발송 예제
     */
    @PostMapping("/send-from-stock-requests")
    public Object sendFromStockRequests(@RequestBody SmsDto smsDto) {

        SingleMessageSentResponse res = smsService.sendFromStockRequests(smsDto);

        if (res == null) {
            return Map.of(
                    "status", "TEST_MODE",
                    "message", "문자는 실제로 발송되지 않았습니다."
            );
        }
        return res;
    }
}
