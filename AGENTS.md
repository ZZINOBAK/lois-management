# Lois Management Agent Instructions

작업을 시작하기 전에 반드시 다음 문서를 확인한다.

- `docs/agent-workflow.md`
- 현재 작업과 관련된 `docs/` 문서

Demo 관련 작업에서는 반드시 다음 문서도 확인한다.

- `docs/demo-requirements.md`

`docs/agent-workflow.md`에 정의된 현재 역할의 규칙을 따른다.

## Communication & Reporting

- 모든 분석 및 작업 결과는 한국어로 보고한다.
- 결론부터 간결하게 작성한다.
- 핵심 결론은 3줄 이내로 요약한다.
- 발견된 문제는 중요도순 최대 5개만 보고한다.
- 문제의 근거는 파일 경로와 메서드명을 명시한다.
- 코드에서 확인된 사실과 추정 사항을 구분한다.
- 상세 설명은 사용자가 요청할 때 제공한다.
- 중요한 위험이나 테스트 실패는 생략하지 않는다.

## Task Scope

- 사용자가 명시한 작업 범위를 준수한다.
- 분석만 요청받은 경우 코드를 수정하지 않는다.
- 구현을 명시적으로 요청받은 경우 해당 범위 내에서 작업한다.
- 작업 중 발견한 다른 문제는 보고하되 임의로 수정하지 않는다.
- 코드 수정 후 변경사항과 테스트 결과를 간결하게 보고한다.

## Task-Specific Instructions

- 로깅 개선 작업 시 `docs/logging-improvement.md`의 범위와 원칙을 따른다.