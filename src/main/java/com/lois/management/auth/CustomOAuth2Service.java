package com.lois.management.auth;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
// 이전 클래스명: com.lois.management.security.CustomOAuth2Service
// 이식 출처: feature/security-spring / src/main/java/com/lois/management/security/CustomOAuth2Service.java
public class CustomOAuth2Service extends DefaultOAuth2UserService {

    private final EmployeeService employeeService;

    //카카오 추가 코드
    // TODO 추후 삭제: 이전 JwtProvider 주입은 이 클래스에서 사용하지 않으므로 주석 처리해 보존한다.
    // private final JwtProvider jwtProvider; // 이전 브랜치의 미사용 주입
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        // google, kakao 등 소셜 종류 확인
        String provider = userRequest.getClientRegistration().getRegistrationId();

        // 소셜에서 제공해주는 사용자 정보를 oAuth2User에 담기
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        if ("kakao".equals(provider)) {
            Map<String, Object> attributes = oAuth2User.getAttributes();

            // kakao 응답 구조 파싱
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            // 사용자 kakao 고유 id 추출
            Object kakaoId = attributes.get("id");
            if (kakaoId == null) {
                throw new OAuth2AuthenticationException(
                        "카카오 사용자 ID가 없습니다."
                );
            }
            String providerUserId = kakaoId.toString();

            // 사용자 이름 추출
            String name = (String) profile.get("nickname");

            // 사용자 이메일 추출
            // 이메일은 사용자가 허가해야 정보 받아올 수 있기 떄문에 null 허용
            String email = (String) kakaoAccount.get("email");

            // 사용자 계정 여부 조회
            Long check = employeeService.findByOauth2Id(provider, providerUserId);;

            // 사용자 계정이 없는 경우 실행
            if (null == check) {
                check = employeeService.createByOAuth2(name, email, "PENDING", provider, providerUserId);

            }

            Employee employee = employeeService.findById(check);

            // 이후 SuccessHandler에서 쓰기 쉽게 attribute 단순화
            Map<String, Object> simpleAttributes = Map.of(
                    "employeeId", employee.getId().toString(),
                    "role", employee.getRole()
            );
            return new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole())),
                    simpleAttributes,
                    "employeeId"
            );
        }
        if("google".equals(provider)) {
            Map<String, Object> attributes = oAuth2User.getAttributes();

            // 사용자 google 고유 id 추출
            Object googleId = attributes.get("sub");
            if (googleId == null) {
                throw new OAuth2AuthenticationException(
                        "구글 사용자 ID가 없습니다."
                );
            }
            String providerUserId = googleId.toString();

            // 사용자 이름 추출
            String name = (String) attributes.get("name");

            // 사용자 이메일 추출
            // 이메일은 사용자가 허가해야 정보 받아올 수 있기 떄문에 null 허용
            String email = (String) attributes.get("email");

            // 사용자 계정 여부 조회
            Long check = employeeService.findByOauth2Id(provider, providerUserId);;

            // 사용자 계정이 없는 경우 실행
            if (check == null) {
                check = employeeService.createByOAuth2(name, email, "PENDING", provider, providerUserId);

            }

            Employee employee = employeeService.findById(check);

            // 이후 SuccessHandler에서 쓰기 쉽게 attribute 단순화
            Map<String, Object> simpleAttributes = Map.of(
                    "employeeId", employee.getId().toString(),
                    "role", employee.getRole()
            );
            return new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole())),
                    simpleAttributes,
                    "employeeId"
            );

        }

        return oAuth2User;
    }

}
