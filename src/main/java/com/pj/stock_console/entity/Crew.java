package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Crew {

    public static final int MAX_MEMBERS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean requireApproval = false;

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewApplication> applications = new ArrayList<>();

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewStrategyLog> strategyLogs = new ArrayList<>();

    public static Crew create(String name, String description, Member leader, boolean requireApproval) {
        Crew crew = new Crew();
        crew.name = name;
        crew.description = description;
        crew.requireApproval = requireApproval;

        CrewMember leaderMember = CrewMember.of(crew, leader, CrewRole.LEADER);
        crew.members.add(leaderMember);
        return crew;
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    public boolean hasMember(Long memberId) {
        return members.stream()
                .anyMatch(cm -> cm.getMember().getId().equals(memberId));
    }

    public Member getLeader() {
        return members.stream()
                .filter(cm -> cm.getRole() == CrewRole.LEADER)
                .map(CrewMember::getMember)
                .findFirst()
                .orElse(null);
    }
}
