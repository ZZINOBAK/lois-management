# 🥐 Lois Management

아르바이트로 근무 중인 카페의 운영 편의를 위해 케이크 예약 관리와 비품 재고 요청 기능을 제공하는 관리자용 웹 애플리케이션

---

## 📌 Problem
### 1. 케이크 예약 관리

- 제작 필요 수량 계산 오류  
- 현장 판매와 예약 수량 충돌  

### 2. 비품 재고 관리

- 수기로 전달되는 재고 요청 방식으로 인해 누락 및 커뮤니케이션 비용 증가  

이러한 운영상의 비효율을 해결하기 위해  
Lois Management 시스템을 개발했습니다.

---

## 💡 Structural Challenge

초기 구현에서는 `reservations` 테이블의 상태값에 의존해 제작 필요 수량을 계산했습니다.

그러나 예약과 현장 판매가 동시에 발생하는 상황에서  
상태 기반 집계 방식은 다음과 같은 한계를 드러냈습니다.

- 상태값만으로는 실제 생산 완료 수량을 정확히 표현하기 어려움  
- 현장 판매 데이터가 집계 로직과 자연스럽게 결합되지 않음  

---

## 💡 Solution

상태 기반 계산 방식을 개선하기 위해  
재고 증감 이력을 독립적으로 관리하는 `cake_movement` 테이블을 도입했습니다.

### 핵심 설계

- 생산(+), 소비(-) 수량을 `delta` 값으로 기록  
- `SUM(delta)` 기반으로 실제 수량 집계  

이를 통해 예약과 현장 판매가 혼재된 상황에서도
일관된 계산 방식을 유지할 수 있었습니다.

---

## 🔄 Domain Flow

- 예약 생성 → 제작(PRODUCED, +1) → 픽업(PICKED, -1)
- 취소(UNDO)
- 현장 판매(ON_SITE)

모든 재고 변경은 이벤트 단위로 기록되며,  
최종 수량은 SUM(delta) 집계를 통해 계산됩니다.

---

## 📦 Stock Request

비품 재고가 임계 수량 이하로 떨어질 경우,
요청 목록을 자동으로 생성하여 프린트 및 문자 전송이 가능하도록 구현했습니다.

이를 통해 수기 전달 과정의 누락 문제를 개선했습니다.

---

## 🔐 Security & Infrastructure

- Spring Security 기반 접근 제어  
- IP Whitelist 필터 적용  
- JWT 기반 인증 구조  
- AWS EC2 배포  
- GitHub Actions 기반 CI/CD 구성  

운영 환경을 고려하여 보안과 배포 자동화 구조를 함께 설계했습니다.

---

## ⚡ Tech Stack

**Backend**  
Java · Spring Boot · MyBatis · Spring Security  

**Database**  
MySQL · Oracle  

**Infrastructure**  
AWS (EC2, RDS) · GitHub Actions · Docker

---

## 🌐 Live Demo

현재 AWS EC2 환경에서 운영 중입니다.  
https://www.lois-management.com

---

## 📚 What I Learned

- 상태 기반 설계의 한계를 경험하며 데이터 구조의 중요성을 체감했습니다.  
- 이벤트 기반 사고를 통해 복잡한 비즈니스 흐름을 단순화할 수 있었습니다.  
- 운영 중 발생하는 예외를 구조적으로 개선하는 경험을 했습니다.  
- 기능 구현을 넘어 데이터 정합성을 고려하는 개발자로 성장하고 있습니다.
