# Lois Management Agent Instructions

## Project
- Java 21
- Spring Boot 3.5.7
- Gradle
- MyBatis 3.0.5 (XML Mapper)
- MySQL
- Thymeleaf
- Spring Security

## Build & Test
- Build: ./gradlew build
- Test: ./gradlew test

## Architecture
- Controller → Service → Mapper 구조를 따른다.
- 기존 프로젝트 구조와 naming convention을 우선한다.
- 기존 기능의 동작을 임의로 변경하지 않는다.

## Safety / Rules
- DB schema 변경은 사용자 승인 없이 수행하지 않는다.
- 새로운 dependency는 사용자 승인 없이 추가하지 않는다.
- 배포 관련 설정은 사용자 승인 없이 변경하지 않는다.