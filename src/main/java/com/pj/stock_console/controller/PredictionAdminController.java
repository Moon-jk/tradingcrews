package com.pj.stock_console.controller;

import com.pj.stock_console.dto.PredictionDto;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Role;
import com.pj.stock_console.repository.MemberRepository;
import com.pj.stock_console.service.PredictionAutomationService;
import com.pj.stock_console.service.PredictionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prediction")
@RequiredArgsConstructor
public class PredictionAdminController {

    private static final String FORBIDDEN_MESSAGE = "권한이 없습니다.";

    private final PredictionService predictionService;
    private final MemberRepository memberRepository;
    private final PredictionAutomationService automationService;

    private boolean isAdmin(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        return loginMember != null && loginMember.getRole() == Role.ADMIN;
    }

    /**
     * 모든 질문 목록 조회
     */
    @GetMapping("/questions")
    public ResponseEntity<?> getAllQuestions(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);

        List<PredictionDto.QuestionResponse> responses = predictionService.getAllQuestions();
        return ResponseEntity.ok(responses);
    }

    /**
     * 질문 생성
     */
    @PostMapping("/questions")
    public ResponseEntity<String> createQuestion(@RequestBody PredictionDto.QuestionRequest request, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);
        
        try {
            predictionService.createQuestion(request);
            return ResponseEntity.ok("질문이 생성되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 베팅 마감
     */
    @PostMapping("/questions/{id}/close")
    public ResponseEntity<String> closeQuestion(@PathVariable(name = "id") Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);

        try {
            predictionService.closePrediction(id);
            return ResponseEntity.ok("베팅이 마감되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 결과 정산
     */
    @PostMapping("/questions/{id}/settle")
    public ResponseEntity<String> settleQuestion(@PathVariable(name = "id") Long id, 
                                          @RequestBody PredictionDto.SettleRequest request, 
                                          HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);

        try {
            predictionService.settlePrediction(id, request.getWinningOptionId());
            
            // 정산 후 자신의 TP가 변경되었을 수 있으므로 세션 최신화
            Member loginMember = (Member) session.getAttribute("loginMember");
            if (loginMember != null) {
                Member updatedMember = memberRepository.findById(loginMember.getId()).orElse(loginMember);
                session.setAttribute("loginMember", updatedMember);
            }
            
            return ResponseEntity.ok("정산이 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 정산 롤백
     */
    @PostMapping("/questions/{id}/rollback")
    public ResponseEntity<String> rollbackQuestion(@PathVariable(name = "id") Long id, 
                                                   @RequestBody(required = false) PredictionDto.RollbackRequest request,
                                                   HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);

        try {
            String reason = (request != null) ? request.getReason() : null;
            predictionService.rollbackSettlement(id, reason);
            
            // 롤백 후 세션 최신화
            Member loginMember = (Member) session.getAttribute("loginMember");
            if (loginMember != null) {
                Member updatedMember = memberRepository.findById(loginMember.getId()).orElse(loginMember);
                session.setAttribute("loginMember", updatedMember);
            }
            
            return ResponseEntity.ok("정산이 롤백되었습니다. 다시 정산할 수 있습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * [자동화] 강제 생성 (테스트용)
     */
    @PostMapping("/automation/create")
    public ResponseEntity<String> forceCreate(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);
        automationService.createDailyStockPrediction();
        return ResponseEntity.ok("자동 예측이 생성되었습니다.");
    }

    /**
     * [자동화] 강제 정산 (테스트용)
     */
    @PostMapping("/automation/settle")
    public ResponseEntity<String> forceSettle(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);
        automationService.settleDailyPredictions();
        return ResponseEntity.ok("자동 정산이 완료되었습니다.");
    }

    /**
     * 질문 삭제
     */
    @DeleteMapping("/questions/{id}")
    public ResponseEntity<String> deleteQuestion(@PathVariable(name = "id") Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(FORBIDDEN_MESSAGE);

        try {
            predictionService.deleteQuestion(id);
            return ResponseEntity.ok("질문이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
