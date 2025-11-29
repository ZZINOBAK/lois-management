//package com.lois.management.api.kakao;
//
//import jakarta.annotation.PostConstruct;
//import net.nurigo.sdk.NurigoApp;
//import net.nurigo.sdk.message.model.Message;
//import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
//import net.nurigo.sdk.message.response.SingleMessageSentResponse;
//import net.nurigo.sdk.message.service.DefaultMessageService;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//@Component
//public class KakaoNotiApiClient {
//
//    // 🔐 환경 변수로 API Key 설정
//    @Value("${solapi.api.key}")
//    private String apiKey;
//
//    @Value("${solapi.api.secret}")
//    private String apiSecret;
//
//    DefaultMessageService messageService;
//
//    // 🚀 초기화 작업 - @PostConstruct
//    @PostConstruct
//    public void init() {
//        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
//    }
//
//    public SingleMessageSentResponse sendKakao(Message message){
//
//        SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message)); // 메시지 발송 요청
//        return response;
//
//    }
//}
