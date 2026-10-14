# PLAN_Notice_Redesign.md

## 개요
- **목표**: `/notice` (공지사항 목록 페이지) UI를 100% Tailwind CSS 유틸리티 클래스 기반의 프리미엄 반응형(카드형 리스트)으로 전면 리디자인한다. PC, 모바일, 그리고 다크/라이트 모드를 완벽 지원한다.
- **클릭 동작**: 기존 방식 유지 (제목 클릭 시 `/notice/detail/{id}` 으로 일반 링크 이동)

## 작업 지시 사항 (Local AI 전용)

### 1단계: 전역 Tailwind 설정 보강 (다크모드 지원)
`src/main/resources/templates/layout.html` 파일을 전체 출력하여 다음을 수정한다.
- `<script>` 태그 내의 `tailwind.config` 객체 설정에 `darkMode: ['class', '[data-theme="dark"]']` 옵션을 추가한다.

### 2단계: 공지사항 리스트 HTML 완전 재작성
`src/main/resources/templates/notice/list.html` 파일을 100% Tailwind CSS 규격에 맞춰 전체 작성(Over-write)한다.
- **인라인 스타일 금지**: 절대로 `style="..."` 속성을 쓰지 말 것. `var(--bg-card)` 등은 CSS를 직접 조작하지 말고 Tailwind `dark:bg-slate-900` 등 기본 제공 클래스로 교체하거나 굳이 필요하면 `bg-[var(--bg-card)]`처럼 테일윈드 룰을 따른다.
- **다크/라이트 테마 자동 변환**: 백그라운드는 `bg-white dark:bg-slate-900`, 보더는 `border border-slate-200 dark:border-white/10`, 텍스트는 `text-slate-800 dark:text-slate-100` 등의 방식을 쓴다.
- **반응형(Responsive) 카드 타입 목록**: 
  - 기본(모바일)화면에서는 제목 위주로 넓은 터치 영역의 카드처럼 보이게 하고, `sm:` 이나 `md:` 브레이크포인트를 사용해 데스크탑일 땐 조회수나 날짜가 가로로 명확히 펼쳐지는 호버 애니메이션 기반의 목록을 만든다.
  - 마우스 오버 시 `group-hover:text-electric transition-all hover:shadow-md hover:-translate-y-0.5` 등으로 프리미엄한 입체감을 준다.
  - `📌 (고정)` 공지사항은 백그라운드가 살짝 돋보이는 글래스모피즘(glassmorphism)이나 Accent 라이트 컬러(`bg-electric/5`) 요소로 구별시킨다.
- **비어 있는 상태(Empty State)**: 게시물이 없을 때 나타나는 UI 역시 Tailwind 기반 다크모드 대응의 미려한 아이콘 UI로 구현한다.
- **Pagination 유지**: 페이징 버튼은 Pill-shape(알약)이나 라운디드 박스형으로 세련되게 꾸민다. 동작을 위해 기존 객체(`notices.number`, `notices.totalPages` 등) 바인딩은 반드시 유지한다.

### 3단계: 컴파일 및 검증
Local AI는 위 두 개의 파일을 모두 덮어쓴 후, 자신의 터미널(Powershell)에서 다음 명령어를 순서대로 자율 실행하여 컴파일 오류 여부를 확인해야 한다.
1. `.\mvnw clean compile` (오류 시 자가 수정 최대 3회)
2. 문제가 없으면 사용자에게 컴파일 성공 여부 보고를 완료할 것.
