# [공지사항 상세 페이지 프리미엄 UI 리디자인 기획안]

## 기획 배경 및 목표
앞서 진행한 공지사항 목록(List) 페이지의 프리미엄화에 맞춰, 상세(Detail) 페이지 역시 100% Tailwind CSS 규칙에 맞게 디자인을 전면 개편합니다. 기존의 `style="color: var(--text-primary)"`와 같은 복잡한 인라인 스타일을 모두 제거하고, Tailwind의 기본 클래스와 `dark:` 유틸리티를 활용해 다크모드/라이트모드 자동 변환 및 프리미엄한 타이포그래피 환경을 제공합니다. 기능(삭제, 좋아요 등)의 변경은 전혀 없습니다.

## 🛠️ 제안하는 수정 사항

### [MODIFY] `src/main/resources/templates/notice/detail.html`
1. **인라인 스타일 박멸 및 테마 동기화**:
   - `style="border-color: var(--border-base)"` -> `border-slate-200 dark:border-white/10`
   - `style="color: var(--text-primary)"` -> `text-slate-900 dark:text-slate-100`
   - `style="background: var(--bg-muted)"` -> `bg-slate-50 dark:bg-navy-950` 등 순수 Tailwind 클래스로 100% 전환.
2. **타이포그래피 및 본문(Article) 레이아웃 강화**:
   - 공지사항 제목(`h1` 태그) 및 본문 내용을 더욱 가독성 높게 조정 (`leading-relaxed`, `text-slate-800 dark:text-slate-200`).
   - 제목 하단의 메타데이터(작성자, 작성일, 조회수)를 깔끔한 `Pill`(알약) 형태의 배지 디자인으로 통일.
   - **본문 영역 가독성 여백**: 전체 카드는 넓게 쓰되, 실제 글이 들어가는 본문 영역은 시야 범위 내로 좁혀 가독성을 높입니다 (`max-w-4xl mx-auto`).
3. **인터랙션 버튼 디자인 (좋아요/싫어요 등)**:
   - "목록으로 돌아가기", "수정", "삭제" 버튼을 심플한 아웃라인(Border) 혹은 부드러운 배경색(bg-slate-100)으로 처리하여 세련미를 더함.
   - 좋아요/싫어요 버튼 클릭 여부에 따라 `bg-electric/10 border-electric` 같이 불이 켜지는 듯한 Glow 효과 적용.
4. **댓글(Discussion) 섹션 업그레이드**:
   - 댓글 작성자의 아바타 디자인에 트렌디한 그라데이션(`bg-gradient-to-tr from-electric to-indigo-500`) 유지.
   - 댓글 입력 폼(`textarea`)을 다크모드에 자연스럽게 녹아들도록 음각(`shadow-inner`) 효과 적용.

## 📝 검증 계획 (Verification Plan)
1. `detail.html` 파일 전체를 Tailwind 규격에 맞춰 수정.
2. 터미널 테스트 `.\mvnw clean compile` 진행하여 문법 및 빌드 오류 방지 확인.
3. 작업 결과 브리핑.
