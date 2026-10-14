package com.pj.stock_console.controller;

import com.pj.stock_console.dto.PredictionDto;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.PredictionQuestion;
import com.pj.stock_console.repository.MemberRepository;
import com.pj.stock_console.service.PredictionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class PredictionApiController {

    private final PredictionService predictionService;
    private final MemberRepository memberRepository;

    /**
     * 활성 질문 목록 조회
     */
    @GetMapping("/questions")
    public ResponseEntity<List<PredictionDto.QuestionResponse>> getActiveQuestions() {
        List<PredictionDto.QuestionResponse> responses = predictionService.getActiveQuestions().stream()
                .map(this::convertToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 최근 정산된 질문 목록 조회
     */
    @GetMapping("/settled-questions")
    public ResponseEntity<List<PredictionDto.QuestionResponse>> getRecentSettledQuestions() {
        List<PredictionDto.QuestionResponse> responses = predictionService.getRecentSettledQuestions().stream()
                .map(this::convertToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 가장 최근 질문 단건 조회
     */
    @GetMapping("/latest-question")
    public ResponseEntity<PredictionDto.QuestionResponse> getLatestQuestion() {
        PredictionQuestion q = predictionService.getLatestQuestion();
        if (q == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(convertToResponse(q));
    }

    /**
     * 최근 질문들 목록 조회 (상위 3개)
     */
    @GetMapping("/recent-questions")
    public ResponseEntity<List<PredictionDto.QuestionResponse>> getRecentQuestions() {
        List<PredictionDto.QuestionResponse> responses = predictionService.getRecentQuestions().stream()
                .map(this::convertToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 질문 상세 조회
     */
    @GetMapping("/questions/{id}")
    public ResponseEntity<PredictionDto.QuestionResponse> getQuestion(@PathVariable(name = "id") Long id) {
        PredictionQuestion question = predictionService.getQuestion(id);
        return ResponseEntity.ok(convertToResponse(question));
    }

    /**
     * 베팅 참여
     */
    @PostMapping("/bet")
    public ResponseEntity<String> placeBet(@RequestBody PredictionDto.BetRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            predictionService.placeBet(loginMember.getId(), request.getOptionId(), request.getAmount());
            
            // 배팅 후 차감된 TP를 세션에 반영
            Member updatedMember = memberRepository.findById(loginMember.getId()).orElse(loginMember);
            session.setAttribute("loginMember", updatedMember);
            
            return ResponseEntity.ok("베팅이 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private PredictionDto.QuestionResponse convertToResponse(PredictionQuestion q) {
        return PredictionDto.QuestionResponse.builder()
                .id(q.getId())
                .title(q.getTitle())
                .status(q.getStatus())
                .totalPool(q.getTotalPool())
                .createdAt(q.getCreatedAt())
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
}
