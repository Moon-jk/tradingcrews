/* ════════════════════════════════════════
   main.js — Market Overview 페이지
   ════════════════════════════════════════ */

document.addEventListener('DOMContentLoaded', () => {
    loadMainPrediction();
    loadWatchlistPrices(); // 🌟 [추가] 관심종목 가격 비동기 로드
});

async function loadWatchlistPrices() {
    const items = document.querySelectorAll('.watchlist-item');
    if (items.length === 0) return;

    const symbols = Array.from(items).map(item => item.getAttribute('data-symbol'));

    try {
        const response = await fetch('/api/stock/prices', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(symbols)
        });

        if (!response.ok) return;
        const prices = await response.json();

        items.forEach(item => {
            const symbol = item.getAttribute('data-symbol');
            const data = prices[symbol];
            if (!data) return;

            const priceEl = item.querySelector('.watchlist-price');
            const changeEl = item.querySelector('.watchlist-change');

            if (priceEl) {
                // 숫자가 아닌 경우(에러 등) 예외 처리
                const price = typeof data.c === 'number' ? data.c : 0;
                priceEl.innerHTML = `$${price.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
            }

            if (changeEl) {
                const dp = data.dp || 0;
                const isPositive = dp >= 0;
                changeEl.textContent = (isPositive ? '+' : '') + dp.toFixed(2) + '%';
                changeEl.className = `text-[10px] font-bold watchlist-change ${isPositive ? 'text-green-500' : 'text-red-500'}`;
            }
        });
    } catch (error) {
        console.error('Failed to load watchlist prices:', error);
    }
}

async function loadMainPrediction() {
    const container = document.getElementById('main-prediction-container');
    if (!container) return;

    try {
        const response = await fetch('/api/prediction/recent-questions');
        if (!response.ok) {
            container.innerHTML = '<div class="text-center py-4 opacity-40 italic text-xs">준비된 질문이 없습니다.</div>';
            return;
        }
        const questions = await response.json();
        
        if (questions.length === 0) {
            container.innerHTML = '<div class="text-center py-4 opacity-40 italic text-xs">준비된 질문이 없습니다.</div>';
            return;
        }

        // 우선순위: 진행 중인(OPEN) 가장 최신 질문 -> 없으면 가장 마지막 질문(정산된 것 포함)
        let displayQ = questions.find(q => q.status === 'OPEN');
        if (!displayQ) {
            displayQ = questions[0]; 
        }

        const q = displayQ;
        const total = q.totalPool;
        const isSettled = q.status === 'SETTLED';
        const isLive = q.status === 'OPEN';

        container.innerHTML = `
            <div class="prediction-item space-y-5">
                <div class="px-1 relative">
                    ${isSettled ? `<span class="absolute -top-4 left-1 px-1.5 py-0.5 rounded border uppercase tracking-widest" style="background: var(--bg-muted); color: var(--text-muted); border-color: var(--border-base); font-size: 8px; font-weight: 900;">FINISHED</span>` : ''}
                    <h4 class="text-sm font-black mb-1.5 mt-2 leading-tight" style="color: var(--text-primary)">${q.title}</h4>
                    <div class="flex justify-between items-center text-[10px] font-bold">
                        <p class="uppercase tracking-tighter" style="color: var(--text-muted)">Pool: <span style="color: var(--accent)">${total.toLocaleString()} TP</span></p>
                        ${isLive ? `<span style="color: var(--accent)" class="animate-pulse flex items-center gap-1">● LIVE</span>` : ''}
                    </div>
                </div>

                <div class="flex flex-col gap-2">
                    ${q.options.map(o => {
                        const ratio = total > 0 ? (o.optionPool / total * 100).toFixed(1) : 0;
                        const isWinner = isSettled && o.isWinner;
                        
                        let borderStyle = 'border: 1px solid var(--border-base)';
                        let bgStyle = 'background: var(--bg-surface)';
                        let textStyle = 'color: var(--text-muted)';
                        let ratioColor = 'var(--text-muted)';
                        
                        if (isWinner) {
                            borderStyle = 'border: 1px solid var(--accent)';
                            bgStyle = 'background: var(--accent-light)';
                            textStyle = 'color: var(--accent); font-weight: 800;';
                            ratioColor = 'var(--accent)';
                        } else if (isLive) {
                            textStyle = 'color: var(--text-primary); font-weight: 600;';
                        }
                        
                        return `
                            <div class="group rounded-xl p-3 flex justify-between items-center relative overflow-hidden transition-all duration-300" 
                                 style="${bgStyle}; ${borderStyle}">
                                <div class="absolute inset-y-0 left-0 bg-blue-500/${isWinner ? '20' : '10'} transition-all" style="width: ${ratio}%"></div>
                                <div class="flex items-center gap-2 relative z-10 w-full pr-4">
                                    <span class="text-[11px] truncate flex-1" style="${textStyle}">${o.content}</span>
                                    ${isWinner ? '<span class="shrink-0 text-[8px] font-black bg-blue-500 text-white px-1.5 py-0.5 rounded uppercase tracking-tighter shadow-sm">WIN</span>' : ''}
                                </div>
                                <span class="shrink-0 text-[10px] font-black relative z-10 tabular-nums" style="color: ${ratioColor}">${ratio}%</span>
                            </div>
                        `;
                    }).join('')}
                </div>

                ${isSettled && q.resultMessage ? `
                    <div class="mt-2 p-3 rounded-xl border text-center" style="background: var(--bg-inset); border-color: var(--border-base)">
                        <p class="text-[10px] font-bold leading-relaxed" style="color: var(--accent)">${q.resultMessage}</p>
                    </div>
                ` : ''}
            </div>
        `;

    } catch (error) {
        console.error('Failed to load main prediction:', error);
        container.innerHTML = '<p class="text-xs text-center text-red-400 py-4">로드 중 오류 발생</p>';
    }
}