package com.lois.management.service;

import com.lois.management.api.sms.SmsApiClient;
import com.lois.management.domain.StockRequest;
import com.lois.management.dto.SmsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {
    @Value("${solapi.api.sender-number}")
    private String senderNumber;

    private final SmsApiClient smsApiClient;
    private final StockRequestService stockRequestService;

    // 🔥 테스트 모드 ON/OFF
    private final boolean TEST_MODE = true;   // true면 문자 안 나감

    public SingleMessageSentResponse sendFromStockRequests(SmsDto smsDto) {
        // 1) 현재 요청 상태인 것들만 가져오기 (이미 그런 쿼리 있을 듯)
        List<StockRequest> requests = stockRequestService.findAll(); // 또는 findAllRequested()

        // 2) 아이템 이름 리스트로 뽑기
        String itemsText = requests.stream()
                .map(req -> "- " + req.getItemName())   // 앞에 "- " 붙임
                .collect(Collectors.joining("\n"));      // 줄바꿈으로 join

        // 3) 문자 내용 만들기
        String text = "[재고 주문 요청]\n" + itemsText;

        smsDto.setText(text);

        return sendOne(smsDto);

    }

    public SingleMessageSentResponse sendOne(SmsDto smsDto) {
        if (TEST_MODE) {
            log.debug("수신자 번호={}, 문자 내용={}", smsDto.getPhoneNumber(), smsDto.getText());
            System.out.println("🧪 [TEST_MODE] 실제 문자는 발송되지 않습니다.");
            return null;   // 객체 생성 안 함
        }

        Message message = new Message();
        // 발신번호 및 수신번호는 반드시 01012345678 형태로 입력되어야 합니다.
        message.setFrom(senderNumber);
        message.setTo(smsDto.getPhoneNumber());
        message.setText(smsDto.getText());

        return smsApiClient.sendSMS(message);
    }


}
