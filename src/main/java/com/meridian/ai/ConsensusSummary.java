package com.meridian.ai;

import com.meridian.proposal.Proposal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * README §13.1 8) Consensus_Summaries
 */
@Entity
@Table(name = "consensus_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsensusSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsensusStatus consensusStatus;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // ponytail: JSON을 TEXT로 저장(JSONB 매핑 의존성 없이). 실제 파싱은 서비스 계층 구현 시 추가.
    @Column(columnDefinition = "TEXT")
    private String keyIssues;

    @Column(columnDefinition = "TEXT")
    private String culturalAnalysis;

    @Column(columnDefinition = "TEXT")
    private String hiddenOpposition;

    @Column(columnDefinition = "TEXT")
    private String recommendedActions;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
