package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = { "crew_id", "member_id" })
})
public class CrewMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrewRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public static CrewMember of(Crew crew, Member member, CrewRole role) {
        CrewMember cm = new CrewMember();
        cm.crew = crew;
        cm.member = member;
        cm.role = role;
        return cm;
    }
}
