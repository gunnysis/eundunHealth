# Antigravity Workspace Customizations

이 `.agents/` 디렉토리는 Antigravity(Gemini) 에이전트의 워크스페이스(프로젝트) 레벨 커스텀 설정을 담는 공간입니다.

디렉토리 구조 및 용도:
- `rules/`: 에이전트가 코딩하거나 질문에 답할 때 지켜야 할 규칙(Rule) 마크다운 파일들을 넣습니다.
- `skills/`: 에이전트가 특정 작업이나 워크플로우를 수행하는 방법을 알려주는 기술(Skill) 파일(`SKILL.md`)을 넣습니다.
- `plugins/`: 여러 룰과 스킬을 묶은 플러그인을 관리합니다.
- `mcp_config.json`: 에이전트가 외부 도구나 API와 연동하기 위해 사용하는 MCP(Model Context Protocol) 서버 설정을 정의합니다.

> 참고: 루트 경로의 `GEMINI.md` 파일도 동일하게 규칙으로 로드됩니다.
