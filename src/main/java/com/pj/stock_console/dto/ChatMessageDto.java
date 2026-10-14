package com.pj.stock_console.dto;

import com.pj.stock_console.entity.CrewMessageType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ChatMessageDto {
    private Long crewId;
    private Long senderId;
    private String senderName;
    private String content;
    private String timestamp;

    // 투표 관련
    private Long messageId;
    private CrewMessageType type = CrewMessageType.TEXT;
    private boolean isClosed = false;
    private List<String> pollOptions;
    private Map<String, Integer> pollResults; // 옵션별 득표수
    private String selectedOption; // 투표 시 선택한 옵션
}
