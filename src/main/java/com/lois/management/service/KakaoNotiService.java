//package com.lois.management.service;
//
//import com.lois.management.api.kakao.KakaoNotiApiClient;
//import lombok.RequiredArgsConstructor;
//import net.nurigo.sdk.message.model.KakaoOption;
//import net.nurigo.sdk.message.model.Message;
//import net.nurigo.sdk.message.response.SingleMessageSentResponse;
//import org.springframework.stereotype.Service;
//
////@Service
//@RequiredArgsConstructor
//public class KakaoNotiService {
//
//    private final KakaoNotiApiClient kakaoNotiApiClient;
//
//    public SingleMessageSentResponse sendOneAta() {
//        KakaoOption kakaoOption = new KakaoOption();
//        // disableSms를 true로 설정하실 경우 문자로 대체발송 되지 않습니다.
//        // kakaoOption.setDisableSms(true);
//
//        // 등록하신 카카오 비즈니스 채널의 pfId를 입력해주세요.
//        kakaoOption.setPfId("");
//        // 등록하신 카카오 알림톡 템플릿의 templateId를 입력해주세요.
//        kakaoOption.setTemplateId("");
//
//        // 알림톡 템플릿 내에 #{변수} 형태가 존재할 경우 variables를 설정해주세요.
//        /*
//        HashMap<String, String> variables = new HashMap<>();
//        variables.put("#{변수명1}", "테스트");
//        variables.put("#{변수명2}", "치환문구 테스트2");
//        kakaoOption.setVariables(variables);
//        */
//
//        Message message = new Message();
//        // 발신번호 및 수신번호는 반드시 01012345678 형태로 입력되어야 합니다.
//        message.setFrom("발신번호 입력");
//        message.setTo("수신번호 입력");
//        message.setKakaoOptions(kakaoOption);
//
//
//        return kakaoNotiApiClient.sendKakao(message);
//    }
//}
