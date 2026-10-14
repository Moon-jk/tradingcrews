package com.pj.stock_console.service;

import com.pj.stock_console.entity.Crew;
import com.pj.stock_console.entity.CrewMember;
import com.pj.stock_console.entity.CrewRole;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.CrewApplication;
import com.pj.stock_console.entity.ApplicationStatus;
import com.pj.stock_console.repository.CrewMemberRepository;
import com.pj.stock_console.repository.CrewRepository;
import com.pj.stock_console.repository.CrewApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewService {

    private final CrewRepository crewRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewApplicationRepository crewApplicationRepository;
    private final com.pj.stock_console.repository.CrewStrategyLogRepository crewStrategyLogRepository;
    private final TradingService tradingService;
    private final NotificationService notificationService;

    // 🌟 [최석화] 크루 랭킹 캐시 저장을 위한 로컬 메모리 변수
    private final Map<Integer, List<CrewRankingEntry>> rankingCache = new ConcurrentHashMap<>();

    private static final int MAX_CREWS_PER_MEMBER = 3;

    /**
     * 크루 생성
     */
    @Transactional
    public Crew createCrew(String name, String description, Member leader, boolean requireApproval) {
        // [3크루 제한] 확인
        validateMaxCrews(leader.getId());

        Crew crew = Crew.create(name, description, leader, requireApproval);
        return crewRepository.save(crew);
    }

    /**
     * 크루 가입
     */
    @Transactional
    public CrewMember joinCrew(Long crewId, Member member, CrewRole role) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 크루입니다."));

        // [7인의 법칙] 초과 여부 확인
        if (crew.isFull()) {
            throw new IllegalStateException(String.format("크루 정원(%d명)이 초과되어 가입할 수 없습니다.", Crew.MAX_MEMBERS));
        }

        // 중복 가입 체크
        if (crew.hasMember(member.getId())) {
            throw new IllegalStateException("이미 가입된 크루입니다.");
        }

        // [3크루 제한] 확인
        validateMaxCrews(member.getId());

        // 크루에 멤버 추가
        CrewMember crewMember = CrewMember.of(crew, member, role);
        crew.getMembers().add(crewMember);

        return crewMemberRepository.save(crewMember);
    }

    /**
     * 크루 참가 신청
     */
    @Transactional
    public void applyCrew(Long crewId, Member member, CrewRole role, String message) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 크루입니다."));

        if (!crew.isRequireApproval()) {
            throw new IllegalStateException("자유 가입 크루입니다. 바로 가입해주세요.");
        }
        if (crew.isFull()) {
            throw new IllegalStateException("크루 정원이 가득 찼습니다.");
        }
        if (crew.hasMember(member.getId())) {
            throw new IllegalStateException("이미 가입된 크루입니다.");
        }
        if (crewApplicationRepository.existsByCrewIdAndMemberIdAndStatus(crewId, member.getId(),
                ApplicationStatus.PENDING)) {
            throw new IllegalStateException("이미 가입 대기 중입니다.");
        }

        validateMaxCrews(member.getId());

        CrewApplication app = CrewApplication.create(crew, member, role, message);
        crewApplicationRepository.save(app);
    }

    /**
     * 참가 신청 승인 (리더만 가능)
     */
    @Transactional
    public void approveApplication(Long appId, Long leaderId) {
        CrewApplication app = crewApplicationRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신청입니다."));

        validateLeader(app.getCrew(), leaderId);

        app.setStatus(ApplicationStatus.APPROVED);
        // 승인 시 joinCrew 호출 (다시한번 정원과 크루제한 검증)
        joinCrew(app.getCrew().getId(), app.getMember(), app.getRequestedRole());
    }

    /**
     * 참가 신청 거부 (리더만 가능)
     */
    @Transactional
    public void rejectApplication(Long appId, Long leaderId) {
        CrewApplication app = crewApplicationRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신청입니다."));

        validateLeader(app.getCrew(), leaderId);
        app.setStatus(ApplicationStatus.REJECTED);
    }

    /**
     * 참가 신청 일괄 승인 (리더만 가능)
     */
    @Transactional
    public int approveAllApplications(Long crewId, Long leaderId) {
        Crew crew = findById(crewId);
        validateLeader(crew, leaderId);

        java.util.List<CrewApplication> pendingApps = crewApplicationRepository.findAllByCrewIdAndStatusOrderByCreatedAtDesc(crewId, ApplicationStatus.PENDING);
        int count = 0;
        for (CrewApplication app : pendingApps) {
            if (crew.isFull()) break;
            app.setStatus(ApplicationStatus.APPROVED);
            joinCrew(app.getCrew().getId(), app.getMember(), app.getRequestedRole());
            count++;
        }
        return count;
    }

    private void validateLeader(Crew crew, Long leaderId) {
        boolean isLeader = crew.getMembers().stream()
                .anyMatch(cm -> cm.getMember().getId().equals(leaderId) && cm.getRole() == CrewRole.LEADER);
        if (!isLeader) {
            throw new IllegalArgumentException("크루 리더만 처리할 수 있습니다.");
        }
    }

    /**
     * 대기 중인 신청 목록 조회 (리더가 열람)
     */
    public List<CrewApplication> getPendingApplications(Long crewId) {
        return crewApplicationRepository.findAllByCrewIdAndStatusOrderByCreatedAtDesc(crewId,
                ApplicationStatus.PENDING);
    }

    /**
     * 특정 회원이 해당 크루에 신청한 가장 최근 내역 조회 (승인/거절/대기 포함)
     */
    public CrewApplication getLatestApplication(Long crewId, Long memberId) {
        return crewApplicationRepository.findFirstByCrewIdAndMemberIdOrderByCreatedAtDesc(crewId, memberId)
                .orElse(null);
    }

    /**
     * 특정 회원이 신청하여 승인 대기 중인 모든 내역 조회
     */
    public List<CrewApplication> getPendingApplicationsByMember(Long memberId) {
        return crewApplicationRepository.findAllByMemberIdAndStatusOrderByCreatedAtDesc(memberId,
                ApplicationStatus.PENDING);
    }

    private void validateMaxCrews(Long memberId) {
        int joinedCrewCount = crewMemberRepository.countByMemberId(memberId);
        if (joinedCrewCount >= MAX_CREWS_PER_MEMBER) {
            throw new IllegalStateException(
                    String.format("최대 %d개의 크루에만 참여할 수 있습니다. 하나 이상의 크루를 탈퇴/삭제 후 다시 생성해주세요.", MAX_CREWS_PER_MEMBER));
        }
    }

    /**
     * 크루 탈퇴 (리더가 탈퇴 시 크루 삭제)
     */
    @Transactional
    public void leaveCrew(Long crewId, Long memberId) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 크루입니다."));

        CrewMember me = crew.getMembers().stream()
                .filter(cm -> cm.getMember().getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 크루의 멤버가 아닙니다."));

        if (me.getRole() == CrewRole.LEADER) {
            // 크루 해체 전 크루원 목록을 먼저 수집 (리더 제외)
            String crewName = crew.getName();
            List<Member> membersToNotify = new ArrayList<>();
            for (CrewMember cm : crew.getMembers()) {
                if (!cm.getMember().getId().equals(memberId)) {
                    membersToNotify.add(cm.getMember());
                }
            }
            // 크루 삭제
            crewRepository.delete(crew);
            // 크루원들에게 알림 발송
            for (Member member : membersToNotify) {
                notificationService.createNotification(
                    member,
                    "📢 크루 해체 알림",
                    "소속 크루 '" + crewName + "'이(가) 크루장에 의해 해체되었습니다.",
                    "/crew"
                );
            }
        } else {
            // 일반 멤버면 내 정보만 삭제
            crew.getMembers().remove(me);
            crewMemberRepository.delete(me);
        }
    }

    /**
     * 전체 크루 목록 조회
     */
    public List<Crew> findAllCrews() {
        return crewRepository.findAll();
    }

    /**
     * 관리자 전용 크루 강제 삭제
     */
    @Transactional
    public void adminDeleteCrew(Long crewId, String reason) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 크루입니다."));

        String crewName = crew.getName();
        String displayReason = (reason == null || reason.isBlank()) ? "사유 미입력" : reason;

        // 모든 크루원에게 알림 발송 (삭제 전)
        for (CrewMember cm : crew.getMembers()) {
            notificationService.createNotification(
                cm.getMember(),
                "🚨 크루 강제 해체 알림",
                String.format("소속 크루 '%s'이(가) 관리자에 의해 강제 해체되었습니다. (사유: %s)", crewName, displayReason),
                "/crew"
            );
        }

        crewRepository.delete(crew);
    }

    /**
     * 내가 가입한 크루 목록 조회
     */
    public List<Crew> findMyCrews(Long memberId) {
        return crewRepository.findAllByMemberId(memberId);
    }

    /**
     * 크루 단건 조회
     */
    public Crew findById(Long crewId) {
        return crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 크루입니다."));
    }

    /**
     * 크루 종합 수익금 계산 (원화 기준)
     */
    public BigDecimal calculateCrewTotalProfitKrw(Long crewId) {
        Crew crew = findById(crewId);
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (CrewMember cm : crew.getMembers()) {
            totalProfit = totalProfit.add(tradingService.calculateMemberTotalProfitKrw(cm.getMember().getId()));
        }

        return totalProfit;
    }

    /**
     * 크루 총 원금 합산 (원화 기준)
     */
    public BigDecimal calculateCrewTotalInitialKrw(Long crewId) {
        Crew crew = findById(crewId);
        BigDecimal totalInitial = BigDecimal.ZERO;

        for (CrewMember cm : crew.getMembers()) {
            totalInitial = totalInitial.add(tradingService.calculateMemberTotalInitialKrw(cm.getMember().getId()));
        }

        return totalInitial;
    }

    /**
     * 크루 전략 일지 조회
     */
    public List<com.pj.stock_console.entity.CrewStrategyLog> getStrategyLogs(Long crewId) {
        return crewStrategyLogRepository.findAllByCrewIdOrderByCreatedAtDesc(crewId);
    }

    /**
     * 크루 전략 일지 새로 추가
     */
    @Transactional
    public void addStrategyLog(Long crewId, String logContent, Member member) {
        Crew crew = findById(crewId);
        if (!crew.hasMember(member.getId())) {
            throw new SecurityException("크루 멤버만 수정할 수 있습니다.");
        }
        com.pj.stock_console.entity.CrewStrategyLog log = com.pj.stock_console.entity.CrewStrategyLog.create(crew, member, logContent);
        crewStrategyLogRepository.save(log);
    }

    /**
     * 🌟 [최적화] 전제 크루 수익률 랭킹 조회 (캐시 기반)
     */
    public List<CrewRankingEntry> getCrewRanking(int limit) {
        List<CrewRankingEntry> cached = rankingCache.get(limit);
        if (cached != null) return cached;

        // 초기 상태만 동기 계산
        return refreshCrewRankingCache(limit);
    }

    /**
     * 🌟 [최적화] 크루 랭킹 연산을 백그라운드에서 매 5분마다 수행하여 사용자 대기 시간을 제로화합니다.
     */
    @Scheduled(fixedRate = 300_000)
    public void updateCrewRankingCache() {
        log.info("🔄 [Scheduled] 크루 랭킹 캐시 갱신 시작...");
        refreshCrewRankingCache(3); // 대시보드용 TOP 3
        refreshCrewRankingCache(100); // 탐색용 등
        log.info("✅ [Scheduled] 크루 랭킹 캐시 갱신 완료");
    }

    @Transactional(readOnly = true)
    public List<CrewRankingEntry> refreshRankingCache(int limit) {
        // 이 메서드는 호출되지 않음 (refreshCrewRankingCache로 대체)
        return refreshCrewRankingCache(limit);
    }

    @Transactional(readOnly = true)
    public List<CrewRankingEntry> refreshCrewRankingCache(int limit) {
        // 1. 모든 크루와 멤버 정보를 한꺼번에 조회 (N+1 방어)
        List<Crew> allCrews = crewRepository.findAll();
        
        // 2. 모든 크루원의 ID 수집 (벌크 계산용)
        Set<Long> allMemberIds = new HashSet<>();
        for (Crew crew : allCrews) {
            for (CrewMember cm : crew.getMembers()) {
                allMemberIds.add(cm.getMember().getId());
            }
        }

        // 3. TradingService를 통해 모든 멤버의 수익률/시드를 단 몇 번의 쿼리로 일괄 계산 (N*M*K 해결)
        Map<Long, TradingService.MemberStat> memberStats = tradingService.getMemberStatsBulk(new ArrayList<>(allMemberIds));

        // 4. 크루별 합산
        List<CrewRankingEntry> entries = new ArrayList<>();
        for (Crew crew : allCrews) {
            BigDecimal crewProfit = BigDecimal.ZERO;
            BigDecimal crewInitial = BigDecimal.ZERO;

            for (CrewMember cm : crew.getMembers()) {
                TradingService.MemberStat stat = memberStats.getOrDefault(cm.getMember().getId(), 
                        new TradingService.MemberStat(BigDecimal.ZERO, BigDecimal.ZERO));
                crewProfit = crewProfit.add(stat.profit());
                crewInitial = crewInitial.add(stat.initial());
            }

            BigDecimal rate = BigDecimal.ZERO;
            if (crewInitial.compareTo(BigDecimal.ZERO) > 0) {
                rate = crewProfit.divide(crewInitial, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }

            entries.add(new CrewRankingEntry(crew.getId(), crew.getName(), rate));
        }

        // 5. 정렬 및 캐시 업데이트
        entries.sort((a, b) -> b.profitRate().compareTo(a.profitRate()));
        List<CrewRankingEntry> result = entries.stream().limit(limit).toList();
        rankingCache.put(limit, result);
        return result;
    }

    public record CrewRankingEntry(
            Long crewId,
            String name,
            BigDecimal profitRate
    ) {}
}
