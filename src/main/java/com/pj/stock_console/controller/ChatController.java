package com.pj.stock_console.controller;

import com.pj.stock_console.dto.ChatMessageDto;
import com.pj.stock_console.entity.*;
import com.pj.stock_console.repository.CrewMessageRepository;
import com.pj.stock_console.repository.CrewRepository;
import com.pj.stock_console.repository.CrewVoteRepository;
import com.pj.stock_console.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final CrewMessageRepository crewMessageRepository;
    private final CrewRepository crewRepository;
    private final MemberRepository memberRepository;
    private final CrewVoteRepository crewVoteRepository;

    @MessageMapping("/chat/{crewId}/sendMessage")
    @SendTo("/topic/crew/{crewId}")
    @Transactional
    public ChatMessageDto sendMessage(@DestinationVariable("crewId") Long crewId,
            @Payload ChatMessageDto chatMessageDto) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid crew"));
        Member sender = memberRepository.findById(chatMessageDto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid user"));

        // 메시지 DB에 엔티티로 저장 (타입 및 투표 옵션 추가)
        CrewMessage message = CrewMessage.create(crew, sender, chatMessageDto.getContent(), chatMessageDto.getType(), chatMessageDto.getPollOptions());
        crewMessageRepository.save(message);

        // 클라이언트로 보낼 닉네임, 전송시간 갱신
        chatMessageDto.setMessageId(message.getId());
        chatMessageDto.setSenderName(sender.getNickname());
        chatMessageDto.setTimestamp(message.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));

        return chatMessageDto;
    }

    @MessageMapping("/chat/{crewId}/vote")
    @SendTo("/topic/crew/{crewId}")
    @Transactional
    public ChatMessageDto votePoll(@DestinationVariable("crewId") Long crewId,
            @Payload ChatMessageDto voteDto) {
        CrewMessage pollMessage = crewMessageRepository.findById(voteDto.getMessageId())
                .orElseThrow(() -> new IllegalArgumentException("Poll message not found"));
        
        if (pollMessage.isClosed()) {
            throw new IllegalStateException("Poll is already closed");
        }

        Member voter = memberRepository.findById(voteDto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Voter not found"));

        // 기존 투표 내역 확인 (중복 투표 방지)
        CrewVote vote = crewVoteRepository.findByPollMessageAndVoter(pollMessage, voter)
                .orElseGet(() -> CrewVote.create(pollMessage, voter, voteDto.getSelectedOption()));
        
        vote.setSelectedOption(voteDto.getSelectedOption());
        crewVoteRepository.save(vote);

        // 현재 투표 현황 계산
        Map<String, Integer> results = getPollResults(pollMessage.getId());
        
        voteDto.setType(CrewMessageType.VOTE_UPDATE);
        voteDto.setPollResults(results);
        return voteDto;
    }

    @MessageMapping("/chat/{crewId}/closePoll")
    @SendTo("/topic/crew/{crewId}")
    @Transactional
    public ChatMessageDto closePoll(@DestinationVariable("crewId") Long crewId,
            @Payload ChatMessageDto closeDto) {
        CrewMessage pollMessage = crewMessageRepository.findById(closeDto.getMessageId())
                .orElseThrow(() -> new IllegalArgumentException("Poll message not found"));
        
        Member requester = memberRepository.findById(closeDto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 작성자 또는 방장만 마감 가능 (방장 로직은 Crew 엔티티의 leader 확인)
        boolean isLeader = pollMessage.getCrew().getMembers().stream()
                .anyMatch(m -> m.getMember().getId().equals(requester.getId()) && m.getRole() == CrewRole.LEADER);
        
        if (!pollMessage.getSender().getId().equals(requester.getId()) && !isLeader) {
            throw new IllegalStateException("Permission denied");
        }

        pollMessage.setClosed(true);
        crewMessageRepository.save(pollMessage);

        closeDto.setType(CrewMessageType.POLL_CLOSE);
        closeDto.setClosed(true);
        closeDto.setPollResults(getPollResults(pollMessage.getId()));
        return closeDto;
    }

    private Map<String, Integer> getPollResults(Long pollMessageId) {
        List<CrewVote> votes = crewVoteRepository.findAllByPollMessageId(pollMessageId);
        Map<String, Integer> results = new HashMap<>();
        for (CrewVote v : votes) {
            results.put(v.getSelectedOption(), results.getOrDefault(v.getSelectedOption(), 0) + 1);
        }
        return results;
    }
}
