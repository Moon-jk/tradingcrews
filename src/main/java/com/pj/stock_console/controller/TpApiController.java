package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.TpHistory;
import com.pj.stock_console.service.TpService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class TpApiController {

    private final TpService tpService;

    @GetMapping("/tp-history")
    public ResponseEntity<List<Map<String, Object>>> getTpHistory(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(401).build();
        }

        List<TpHistory> historyList = tpService.getHistory(loginMember);
        List<Map<String, Object>> response = historyList.stream()
                .map(h -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("amount", h.getAmount());
                    map.put("balance", h.getBalance() != null ? h.getBalance() : 0);
                    map.put("description", h.getDescription());
                    map.put("createdAt", h.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
