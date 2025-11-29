//package com.lois.management.controller;
//
//import com.lois.management.service.KakaoNotiService;
//import lombok.RequiredArgsConstructor;
//import net.nurigo.sdk.message.response.SingleMessageSentResponse;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
////@RestController
////@RequestMapping("/kakao")
//@RequiredArgsConstructor
//public class KakaoNotiController {
//
//    private final KakaoNotiService kakaoNotiService;
//
//    /**
//     * 알림톡 한건 발송 예제
//     */
//    @PostMapping("/send-one-ata")
//    public SingleMessageSentResponse sendOneAta() {
//
//        return kakaoNotiService.sendOneAta();
//    }
//}
