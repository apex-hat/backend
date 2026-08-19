package com.meridian.friend;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 두 사용자 간 친구 요청 한 건. 수락되면 그 자체가 친구 관계를 나타낸다(별도 friendships 테이블 없음).
 */
@Entity
@Table(name = "friend_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendRequestStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant respondedAt;
}
