package com.pj.stock_console.service;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.TpHistory;
import com.pj.stock_console.repository.MemberRepository;
import com.pj.stock_console.repository.TpHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TpService {

    private final MemberRepository memberRepository;
    private final TpHistoryRepository tpHistoryRepository;

    /**
     * TP 변경 및 이력 생성
     */
    @Transactional
    public void changeTp(Member member, int amount, String description) {
        // 회원 최신 정보 조회 (혹은 영속성 컨텍스트 보장)
        Member m = memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        int oldBalance = m.getTp();
        int newBalance = oldBalance + amount;

        if (newBalance < 0) {
            throw new IllegalStateException("Not enough TP.");
        }

        m.setTp(newBalance);
        memberRepository.save(m);

        TpHistory history = TpHistory.builder()
                .member(m)
                .amount(amount)
                .balance(newBalance)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        tpHistoryRepository.save(history);
        
        // 원본 객체(세션 등에 있을 수 있음)의 상태도 업데이트 (필요한 경우)
        member.setTp(newBalance);
    }

    /**
     * 회원의 TP 내역 조회
     */
    public List<TpHistory> getHistory(Member member) {
        return tpHistoryRepository.findAllByMemberOrderByCreatedAtDesc(member);
    }
}
