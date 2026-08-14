package com.apex.meridian.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_user_unread",
                        columnList = "user_id, is_read"
                )
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "user_id",
            nullable = false
    )
    private Long userId;

    @Column(
            name = "proposal_id"
    )
    private Long proposalId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false,
            length = 30
    )
    private NotificationType type;

    @Column(
            name = "message",
            nullable = false,
            length = 300
    )
    private String message;

    @Column(
            name = "is_read",
            nullable = false
    )
    private boolean read = false;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            Long userId,
            Long proposalId,
            NotificationType type,
            String message
    ) {
        this.userId = userId;
        this.proposalId = proposalId;
        this.type = type;
        this.message = message;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public void markAsRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}