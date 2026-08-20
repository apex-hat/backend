package com.meridian.proposal;

import com.meridian.team.Team;
import com.meridian.user.User;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * README §13.1 4) Proposals
 */
@Entity
@Table(name = "proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_team_id", nullable = false)
    private Team targetTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    private Instant deadline;

    /**
     * 마감 임박 알림(DEADLINE_APPROACHING)을 이미 보냈는지. null/false는 아직 안 보낸 것으로 취급한다(기존 행과의 호환을 위해 nullable).
     * {@link org.hibernate.annotations.OptimisticLock}(excluded = true)로 버전 증가 대상에서 제외한다 — 그렇지 않으면
     * DeadlineReminderScheduler의 백그라운드 업데이트가 이 필드만 바꿔도 version이 올라가서, 동시에 들어온 사용자의
     * 제안 수정/삭제 요청이 실제 내용 충돌이 없는데도 낙관적 잠금 충돌(409 CONFLICTING_UPDATE)로 실패할 수 있다.
     */
    @org.hibernate.annotations.OptimisticLock(excluded = true)
    private Boolean deadlineReminderSent;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private Instant completedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;
}
