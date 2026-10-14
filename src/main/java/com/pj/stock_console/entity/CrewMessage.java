package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CrewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrewMessageType type = CrewMessageType.TEXT;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isClosed = false;

    @ElementCollection
    @CollectionTable(name = "crew_message_poll_options", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "option_text")
    @OrderColumn
    private List<String> pollOptions = new ArrayList<>();

    @OneToMany(mappedBy = "pollMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CrewVote> votes = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Map<String, Integer> getVoteResults() {
        Map<String, Integer> results = new HashMap<>();
        for (String opt : pollOptions) {
            results.put(opt, 0);
        }
        for (CrewVote v : votes) {
            results.put(v.getSelectedOption(), results.getOrDefault(v.getSelectedOption(), 0) + 1);
        }
        return results;
    }

    public boolean isVotedBy(Long memberId) {
        return votes.stream().anyMatch(v -> v.getVoter().getId().equals(memberId));
    }

    public int getMaxVoteCount() {
        Map<String, Integer> results = getVoteResults();
        if (results.isEmpty()) return 0;
        return results.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public static CrewMessage create(Crew crew, Member sender, String content, CrewMessageType type, List<String> pollOptions) {
        CrewMessage message = new CrewMessage();
        message.crew = crew;
        message.sender = sender;
        message.content = content;
        message.type = type;
        if (pollOptions != null) {
            message.pollOptions.addAll(pollOptions);
        }
        return message;
    }
}
