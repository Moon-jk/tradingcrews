# [크루 대시보드(Crew List) 프리미엄 UI 리디자인 기획안]

## 1. 기획 배경 및 목표
현재 `http://localhost:8090/crew` 화면은 `<style>` 태그에 하드코딩된 대량의 Custom CSS(글래스모피즘, 카드 UI 등)에 의존하고 있어, 현재 프로젝트의 기본 원칙인 **"100% Tailwind CSS 사용"** 규정에 크게 위배됩니다. 또한 다크모드/라이트모드 대응이 완벽하지 않습니다. 
이에 따라, 기존의 훌륭한 레이아웃 골격을 유지하면서 모든 CSS 코드를 Tailwind 유틸리티 클래스로 변환하고, 시각적 디테일을 한 단계 더 프리미엄 급으로 끌어올리겠습니다.

## 2. 🛠️ 제안 및 확정된 수정 사항

### [MODIFY] `src/main/resources/templates/crew/list.html`
1. **`<style>` 블록 완벽 철거 및 Tailwind 100% 전환**:
   - `crew-card`, `tab-nav`, `info-stat-row` 등에 묶여있던 커스텀 CSS를 파일에서 전부 삭제합니다.
   - 대체 방안: `bg-slate-50 dark:bg-navy-900/50 backdrop-blur-md rounded-3xl border border-slate-200 dark:border-white/10 transition-all duration-300 group hover:-translate-y-2 hover:shadow-2xl` 등의 최고급 Tailwind 레이어링 기술을 사용합니다.
2. **다크모드/라이트모드 완벽 대응**:
   - 기존의 강제 어두운 테마(`var(--glass-bg)`, `var(--bg-card)`)에서 벗어나 낮에는 밝고 깔끔한 화이트톤 섀도우를, 밤에는 고급스러운 진한 네이비 톤(`navy-900`) 글래스모피즘이 자동으로 적용되게 렌더링합니다.
3. **골격 UI(Skeleton) 및 통계 정보 영역 세련미 강화**:
   - PNL(수익률)을 보여주는 로딩 스켈레톤 애니메이션에 Tailwind의 `animate-pulse`를 활용하여 더욱 자연스러운 로딩뷰를 선사합니다.
   - 수익/손실 표기 시 양수/음수 색상도 Tailwind의 `text-emerald-500 bg-emerald-500/10` vs `text-rose-500 bg-rose-500/10` 로 트렌디하게 디자인합니다.
4. **리더 생성 모달(Modal) 재설계**:
   - `modal-overlay`와 `modal-container` 역시 Tailwind의 `fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm` 등으로 우아하게 표현. 
   - 폼 컨트롤 UI(Input, Radio 버튼)를 프론트엔드 전체 디자인 톤에 맞추어 통일.
5. **상단 탭 네비게이션 비주얼 폼 (결정사항)**
   - 프로젝트 컨셉(정예 트레이딩 크루)에 맞춰 첨단 UI 느낌이 나는 **둥근 알약형 스위치 폼(A안)** 방식을 채택하며 이를 100% Tailwind로 구현.

## 3. 검증 계획 (Verification Plan)
1. `crew/list.html` 파일 전체를 Tailwind 규격에 맞춰 수정.
2. 터미널 테스트 `.\mvnw clean compile` 진행하여 빌드 오류 방지 확인.
3. 결과 브리핑.
