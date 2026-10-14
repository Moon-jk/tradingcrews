package com.pj.stock_console.service;

import com.pj.stock_console.dto.PredictionDto;
import com.pj.stock_console.entity.*;
import com.pj.stock_console.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionService {

    private final PredictionQuestionRepository questionRepository;
    private final PredictionOptionRepository optionRepository;
    private final PredictionBetRepository betRepository;
    private final MemberRepository memberRepository;
    private final TpService tpService;

    /**
     * 질문 생성 (관리자)
     */
    @Transactional
    public PredictionQuestion createQuestion(PredictionDto.QuestionRequest request) {
        PredictionQuestion question = PredictionQuestion.builder()
                .title(request.getTitle())
                .status(PredictionStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();

        List<PredictionOption> options = request.getOptions().stream()
                .map(content -> PredictionOption.builder()
                        .question(question)
                        .content(content)
                        .build())
                .toList();

        question.setOptions(options);
        return questionRepository.save(question);
    }

    /**
     * 활성 질문 목록 조회
     */
    public List<PredictionQuestion> getActiveQuestions() {
        List<PredictionQuestion> questions = questionRepository.findAllByStatusInWithItems(
                List.of(PredictionStatus.OPEN, PredictionStatus.CLOSED));
        questions.forEach(q -> q.getOptions().size()); // 강제 초기화
        return questions;
    }

    /**
     * 모든 질문 목록 조회 (관리자용)
     */
    public List<PredictionDto.QuestionResponse> getAllQuestions() {
        return questionRepository.findAllWithItems().stream()
                .map(this::convertToQuestionResponse)
                .toList();
    }

    private PredictionDto.QuestionResponse convertToQuestionResponse(PredictionQuestion q) {
        return PredictionDto.QuestionResponse.builder()
                .id(q.getId())
                .title(q.getTitle())
                .status(q.getStatus())
                .totalPool(q.getTotalPool())
                .createdAt(q.getCreatedAt())
                .closedAt(q.getClosedAt())
                .settledAt(q.getSettledAt())
                .resultMessage(q.getResultMessage())
                .options(q.getOptions().stream()
                        .map(o -> PredictionDto.OptionResponse.builder()
                                .id(o.getId())
                                .content(o.getContent())
                                .optionPool(o.getOptionPool())
                                .isWinner(o.getIsWinner())
                                .build())
                        .toList())
                .build();
    }

    /**
     * 최근 정산된 질문 목록 조회
     */
    public List<PredictionQuestion> getRecentSettledQuestions() {
        List<PredictionQuestion> questions = questionRepository.findTop5RecentSettledWithItems(PredictionStatus.SETTLED);
        questions.forEach(q -> q.getOptions().size()); // 강제 초기화
        return questions;
    }

    /**
     * 가장 최근 질문 단건 조회
     */
    public PredictionQuestion getLatestQuestion() {
        List<PredictionQuestion> list = questionRepository.findTop3RecentWithItems();
        if (list.isEmpty()) return null;
        
        PredictionQuestion q = list.get(0);
        q.getOptions().size();
        return q;
    }

    /**
     * 최근 질문들 조회 (상위 3개)
     */
    public List<PredictionQuestion> getRecentQuestions() {
        List<PredictionQuestion> list = questionRepository.findTop3RecentWithItems();
        list.forEach(q -> q.getOptions().size());
        return list;
    }

    /**
     * 질문 상세 조회
     */
    public PredictionQuestion getQuestion(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));
    }

    /**
     * 베팅 마감 (관리자)
     */
    @Transactional
    public void closePrediction(Long questionId) {
        PredictionQuestion question = getQuestion(questionId);
        if (question.getStatus() != PredictionStatus.OPEN) {
            throw new IllegalStateException("Only OPEN questions can be closed.");
        }
        question.setStatus(PredictionStatus.CLOSED);
        question.setClosedAt(LocalDateTime.now());
    }

    /**
     * 베팅 참여
     */
    @Transactional
    public PredictionBet placeBet(Long userId, Long optionId, Long amount) {
        // 1. 최소 베팅 금액 확인
        if (amount < 100) {
            throw new IllegalArgumentException("Minimum bet amount is 100 TP.");
        }

        // 2. 항목 및 질문 조회
        PredictionOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found."));
        PredictionQuestion question = option.getQuestion();

        // 3. 질문 상태 확인 (OPEN만 가능)
        if (question.getStatus() != PredictionStatus.OPEN) {
            throw new IllegalStateException("Betting is only allowed for OPEN questions.");
        }

        // 4. 중복 참여 확인 (1인 1회)
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        
        java.util.Optional<PredictionBet> existingBet = betRepository.findByUserAndOptionQuestion(member, question);
        if (existingBet.isPresent()) {
            throw new IllegalStateException("이미 이 예측에 참여하셨습니다. (베팅 금액: " + existingBet.get().getAmount() + " TP)");
        }

        // 5. 사용자 TP 확인 및 차감
        if (member.getTp() < amount) {
            throw new IllegalStateException("Not enough TP.");
        }
        tpService.changeTp(member, -amount.intValue(), "예측 베팅 참여: " + question.getTitle());

        // 6. 베팅 저장
        PredictionBet bet = PredictionBet.builder()
                .user(member)
                .option(option)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        betRepository.save(bet);

        // 7. 풀(Pool) 업데이트
        option.addOptionPool(amount);
        question.addTotalPool(amount);

        return bet;
    }

    /**
     * 결과 정산
     */
    @Transactional
    public void settlePrediction(Long questionId, Long winningOptionId) {
        PredictionQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));

        if (question.getStatus() == PredictionStatus.SETTLED) {
            throw new IllegalStateException("Question already settled.");
        }

        PredictionOption winningOption = optionRepository.findById(winningOptionId)
                .orElseThrow(() -> new IllegalArgumentException("Winning option not found."));

        if (!winningOption.getQuestion().getId().equals(questionId)) {
            throw new IllegalArgumentException("The winning option does not belong to this question.");
        }

        // 1. 상태 및 승리 옵션 설정
        question.setStatus(PredictionStatus.SETTLED);
        question.setSettledAt(LocalDateTime.now());
        winningOption.setIsWinner(true);

        // 2. 배당금 계산 및 지급
        long totalPool = question.getTotalPool();
        long winningOptionPool = winningOption.getOptionPool();

        if (winningOptionPool > 0) {
            double effectivePool = totalPool * 0.9; // 10% 수수료 제외
            List<PredictionBet> winnerBets = betRepository.findAllByOption(winningOption);

            for (PredictionBet bet : winnerBets) {
                // reward = (amount / optionPool) * (totalPool * 0.9)
                double rewardRatio = (double) bet.getAmount() / winningOptionPool;
                long reward = (long) Math.floor(rewardRatio * effectivePool);
                
                bet.setReward(reward);
                
                Member user = bet.getUser();
                tpService.changeTp(user, (int) reward, "예측 베팅 적중 보상: " + question.getTitle());
            }
        }
    }

    /**
     * 결과 정산 롤백 (관리자)
     * 오지급된 보상금을 회수하고 질문 상태를 CLOSED로 돌립니다.
     */
    @Transactional
    public void rollbackSettlement(Long questionId, String reason) {
        PredictionQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));

        if (question.getStatus() != PredictionStatus.SETTLED) {
            throw new IllegalStateException("Only SETTLED questions can be rolled back.");
        }

        String logReason = (reason == null || reason.trim().isEmpty()) 
                ? "예측 정산 오류 롤백: " + question.getTitle()
                : "예측 롤백(" + reason + "): " + question.getTitle();

        // 1. 모든 옵션의 베팅 내역을 확인하여 지급된 보상금 회수
        for (PredictionOption option : question.getOptions()) {
            List<PredictionBet> bets = betRepository.findAllByOption(option);
            for (PredictionBet bet : bets) {
                if (bet.getReward() != null && bet.getReward() > 0) {
                    long rewardToTakeBack = bet.getReward();
                    Member user = bet.getUser();
                    
                    // 보상금 회수 (마이너스 처리)
                    tpService.changeTp(user, (int) -rewardToTakeBack, logReason);
                    
                    // 베팅 보상 정보 초기화
                    bet.setReward(0L);
                }
            }
            // 옵션의 승리 여부 초기화
            option.setIsWinner(false);
        }

        // 2. 질문 상태 원복 (마감 상태로 변경하여 다시 정산 가능하게 함)
        question.setStatus(PredictionStatus.CLOSED);
        question.setSettledAt(null);
        question.setResultMessage(null);
    }

    /**
     * 결과 무효 및 환불 (보합 등)
     */
    @Transactional
    public void refundPrediction(Long questionId) {
        PredictionQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));

        if (question.getStatus() == PredictionStatus.SETTLED) {
            throw new IllegalStateException("Question already settled.");
        }

        // 1. 상태 설정
        question.setStatus(PredictionStatus.SETTLED); // 혹은 별도의 CANCELLED/VOID 상태
        question.setSettledAt(LocalDateTime.now());

        // 2. 모든 베팅금 환불
        for (PredictionOption option : question.getOptions()) {
            List<PredictionBet> bets = betRepository.findAllByOption(option);
            for (PredictionBet bet : bets) {
                Member user = bet.getUser();
                tpService.changeTp(user, bet.getAmount().intValue(), "예측 취소 환불: " + question.getTitle());
                bet.setReward(bet.getAmount()); // 원금 환불 표시
            }
        }
    }

    /**
     * 예측 질문 삭제 (관리자)
     */
    @Transactional
    public void deleteQuestion(Long questionId) {
        PredictionQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));

        // 정산되지 않은 예측이면 베팅금 환불 후 삭제
        if (question.getStatus() != PredictionStatus.SETTLED) {
            for (PredictionOption option : question.getOptions()) {
                List<PredictionBet> bets = betRepository.findAllByOption(option);
                for (PredictionBet bet : bets) {
                    tpService.changeTp(bet.getUser(), bet.getAmount().intValue(), "예측 삭제 환불: " + question.getTitle());
                }
            }
        }

        questionRepository.delete(question);
    }
}
