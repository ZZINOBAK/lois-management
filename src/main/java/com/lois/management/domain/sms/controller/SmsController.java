package com.lois.management.domain.sms.controller;

import com.lois.management.domain.sms.service.SmsService;
import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import org.springframework.web.bind.annotation.PostMapping;
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
    public Map<String, Object> sendFromStockRequests() {

        SingleMessageSentResponse res = smsService.sendFromStockRequests();

        if (res == null) {
            return Map.of(
                    "status", "TEST_MODE",
                    "message", "문자는 실제로 발송되지 않았습니다."
            );
        }
        return Map.of("status", "SENT");
    }

    /**
     * 단일 메시지 발송 예제
     */
    @PostMapping("/send-from-stock-requests-test")
    public Object sendFromStockRequestsTest() {

        SingleMessageSentResponse res = smsService.sendFromStockRequests();

        if (res == null) {
            return Map.of(
                    "status", "TEST_MODE",
                    "message", "문자는 실제로 발송되지 않았습니다."
            );
        }
        return res;
    }
}
