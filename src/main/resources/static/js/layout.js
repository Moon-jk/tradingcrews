/* ════════════════════════════════════════
   layout.js — TradingCrews:트루 공통 레이아웃
   
   주의: IS_LOGGED_IN 변수는 layout.html에서
   Thymeleaf 인라인 스크립트로 주입됩니다.
   ════════════════════════════════════════ */

/* ── 테마 관리 ── */
const THEME_KEY = 'tradingcrews-theme';

function getTheme() {
  return localStorage.getItem(THEME_KEY) || 'light';
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem(THEME_KEY, theme);
}

function toggleTheme() {
  applyTheme(getTheme() === 'light' ? 'dark' : 'light');
}

// 페이지 깜빡임 방지: 스크립트 실행 즉시 테마 적용
// (layout.html의 <head> 인라인 스크립트에서 호출됩니다)
applyTheme(getTheme());

/* ── 사이드바 / 프로필 패널 ── */
function toggleSidebar() {
  document.body.classList.toggle('sidebar-open');
}

function toggleProfilePanel() {
  if (!IS_LOGGED_IN) {
    window.location.href = '/login';
    return;
  }
  document.body.classList.toggle('profile-open');
  if (document.body.classList.contains('profile-open')) {
    renderRecentSymbols();
    if (typeof loadNotifications === 'function') {
      loadNotifications();
    }
  }
}

function toggleAdminMenu() {
  const submenu = document.getElementById('admin-submenu');
  const arrow = document.getElementById('admin-arrow');
  if (!submenu || !arrow) return;

  const isHidden = submenu.classList.contains('hidden');
  if (isHidden) {
    submenu.classList.remove('hidden');
    arrow.style.transform = 'rotate(180deg)';
  } else {
    submenu.classList.add('hidden');
    arrow.style.transform = 'rotate(0deg)';
  }
}

function toggleInfoMenu(e) {
  if (e) e.preventDefault();
  const submenu = document.getElementById('info-submenu');
  const arrow = document.getElementById('info-arrow');
  if (!submenu || !arrow) return;

  const isHidden = submenu.classList.contains('hidden');
  if (isHidden) {
    submenu.classList.remove('hidden');
    arrow.style.transform = 'rotate(180deg)';
  } else {
    submenu.classList.add('hidden');
    arrow.style.transform = 'rotate(0deg)';
  }
}

function toggleCommunityMenu(e) {
  if (e) e.preventDefault();
  const submenu = document.getElementById('community-submenu');
  const arrow = document.getElementById('community-arrow');
  if (!submenu || !arrow) return;

  const isHidden = submenu.classList.contains('hidden');
  if (isHidden) {
    submenu.classList.remove('hidden');
    arrow.style.transform = 'rotate(180deg)';
  } else {
    submenu.classList.add('hidden');
    arrow.style.transform = 'rotate(0deg)';
  }
}


/* ── 최근 본 종목 ── */
const RECENT_KEY = 'recent_symbols';
const RECENT_MAX = 5;

function saveSymbol(symbol) {
  if (!symbol?.trim()) return;
  const s = symbol.trim().toUpperCase();
  const list = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
  const updated = [s, ...list.filter(item => item !== s)].slice(0, RECENT_MAX);
  localStorage.setItem(RECENT_KEY, JSON.stringify(updated));
}

function renderRecentSymbols() {
  const container = document.getElementById('recent-symbols-list');
  if (!container) return; // 요소가 없으면 중단 (에러 방지)

  const symbols = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');

  if (symbols.length === 0) {
    container.innerHTML = `
      <div style="color:var(--text-muted); font-size:10px; text-align:center; padding:1.5rem;
                  border:1px dashed var(--border-base); border-radius:1rem; font-style:italic;">
        최근 본 종목이 없습니다.
      </div>`;
    return;
  }

  container.innerHTML = symbols.map(s => `
    <a href="/chart-search?symbol=${s}"
       style="display:flex; align-items:center; justify-content:space-between; padding:0.75rem;
              background:var(--bg-surface-2); border:1px solid var(--border-base); border-radius:0.75rem;
              text-decoration:none; margin-bottom:0.5rem; transition:all 0.2s ease;"
       onmouseover="this.style.borderColor='var(--accent)'; this.querySelector('.sym').style.color='var(--accent)'"
       onmouseout="this.style.borderColor='var(--border-base)'; this.querySelector('.sym').style.color='var(--text-secondary)'">
      <span class="sym" style="font-size:11px; font-weight:700; color:var(--text-secondary);">${s}</span>
      <svg width="12" height="12" fill="none" stroke="var(--text-muted)" viewBox="0 0 24 24">
        <path d="M9 5l7 7-7 7" stroke-width="2.5"/>
      </svg>
    </a>
  `).join('');
}

function clearRecentSymbols() {
  localStorage.removeItem(RECENT_KEY);
  renderRecentSymbols();
}

/* ── 공용 확인/알림 모달 제어 ── */
let confirmCallback = null;

function showConfirmModal(title, message, onConfirm) {
  const modal = document.getElementById('confirm-modal');
  const content = document.getElementById('confirm-modal-content');
  const titleElem = document.getElementById('confirm-modal-title');
  const messageElem = document.getElementById('confirm-modal-message');
  
  if (!modal || !content) return;
  
  titleElem.textContent = title;
  messageElem.textContent = message;
  confirmCallback = onConfirm;
  
  modal.classList.remove('hidden');
  modal.classList.add('flex');
  setTimeout(() => {
    content.classList.remove('opacity-0', 'scale-95');
    content.classList.add('opacity-100', 'scale-100');
  }, 10);
}

function closeConfirmModal() {
  const modal = document.getElementById('confirm-modal');
  const content = document.getElementById('confirm-modal-content');
  if (!modal || !content) return;
  
  content.classList.remove('opacity-100', 'scale-100');
  content.classList.add('opacity-0', 'scale-95');
  setTimeout(() => {
    modal.classList.add('hidden');
    modal.classList.remove('flex');
  }, 300);
}

/* ── 자동완성 ── */
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function fetchStockSuggestions(keyword) {
  fetch(`/api/stock/autocomplete?keyword=${encodeURIComponent(keyword)}`)
    .then(res => res.json())
    .then(renderSuggestions)
    .catch(err => console.error('자동완성 에러:', err));
}

function renderSuggestions(stocks) {
  const list = document.getElementById('suggestion-list');
  list.innerHTML = '';

  if (!stocks.length) {
    list.classList.add('hidden');
    return;
  }

  stocks.forEach(stock => {
    const li = document.createElement('li');
    li.className = 'px-4 py-3 cursor-pointer transition-colors';
    li.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div style="display:flex; flex-direction:column;">
          <span style="color:var(--accent); font-weight:700; font-size:14px;">${stock.symbol}</span>
          <span style="color:var(--text-secondary); font-size:12px; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
            ${stock.nameKo || stock.nameEn}
          </span>
        </div>
        <span style="color:var(--text-muted); font-size:10px; background:var(--bg-muted);
                     padding:2px 6px; border-radius:4px; border:1px solid var(--border-base);">
          ${stock.market}
        </span>
      </div>`;
    li.addEventListener('click', () => {
      document.getElementById('search-input').value = stock.symbol;
      saveSymbol(stock.symbol);
      window.location.href = `/chart-search?symbol=${stock.symbol}`;
    });
    list.appendChild(li);
  });

  list.classList.remove('hidden');
}

/* ── 초기화 ── */
document.addEventListener('DOMContentLoaded', () => {
  // 사이드바 활성 링크
  const path = window.location.pathname;
  document.querySelectorAll('#sidebar-nav a').forEach(link => {
    const href = link.getAttribute('href');
    // 정확히 일치하거나, 하위 경로인 경우 (단, 홈(/) 제외)
    if (href === path || (href !== '/' && path.startsWith(href))) {
      link.classList.add('active');
    }
  });

  // 테마 토글 버튼
  document.getElementById('theme-toggle')?.addEventListener('click', toggleTheme);

  // 자동완성
  const searchInput = document.getElementById('search-input');
  const suggestionList = document.getElementById('suggestion-list');

  if (searchInput && suggestionList) {
    searchInput.addEventListener('input', debounce(e => {
      const keyword = e.target.value.trim();
      if (keyword.length < 1) { suggestionList.classList.add('hidden'); return; }
      fetchStockSuggestions(keyword);
    }, 300));

    document.addEventListener('click', e => {
      if (!searchInput.contains(e.target) && !suggestionList.contains(e.target)) {
        suggestionList.classList.add('hidden');
      }
    });
  }

  // 🔐 비로그인 사용자 상호작용 제한 (커스텀 모달)
  document.addEventListener('click', e => {
    const authElem = e.target.closest('.auth-required');
    if (authElem && !IS_LOGGED_IN) {
      e.preventDefault();
      e.stopPropagation();
      showConfirmModal('로그인 필요', '로그인이 필요한 서비스입니다. 로그인 페이지로 이동하시겠습니까?', () => {
        window.location.href = '/login?redirectURL=' + encodeURIComponent(window.location.pathname);
      });
    }
  }, true);

  // 모달 버튼 이벤트 바인딩
  document.getElementById('confirm-modal-ok')?.addEventListener('click', () => {
    if (confirmCallback) confirmCallback();
    closeConfirmModal();
  });
  document.getElementById('confirm-modal-cancel')?.addEventListener('click', closeConfirmModal);
  document.getElementById('confirm-modal-backdrop')?.addEventListener('click', closeConfirmModal);
});

/* ── 페이지 로딩 제어 ── */
window.addEventListener('load', () => {
  const loader = document.getElementById('page-loader');
  if (loader) loader.classList.add('loaded');
});

window.addEventListener('beforeunload', () => {
  const loader = document.getElementById('page-loader');
  if (loader) loader.classList.remove('loaded');
});

window.addEventListener('pageshow', (event) => {
  if (event.persisted) { // 뒤로 가기 캐시에서 복원된 경우
    const loader = document.getElementById('page-loader');
    if (loader) loader.classList.add('loaded');
  }
});