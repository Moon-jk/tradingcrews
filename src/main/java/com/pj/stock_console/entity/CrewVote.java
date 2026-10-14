package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CrewVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_message_id", nullable = false)
    private CrewMessage pollMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private Member voter;

    @Column(nullable = false)
    private String selectedOption;

    public static CrewVote create(CrewMessage pollMessage, Member voter, String selectedOption) {
        CrewVote vote = new CrewVote();
        vote.pollMessage = pollMessage;
        vote.voter = voter;
        vote.selectedOption = selectedOption;
        return vote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrewVote crewVote = (CrewVote) o;
        return Objects.equals(id, crewVote.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
