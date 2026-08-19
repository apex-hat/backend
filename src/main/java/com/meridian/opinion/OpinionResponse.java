package com.meridian.opinion;

import java.time.Instant;

/**
 * README §8 의견 응답 Body. {@code content}는 entity의 {@link Opinion#getComment()}에 대응한다.
 */
public record OpinionResponse(
        Long id,
        Long proposalId,
        Long userId,
        OpinionStance stance,
        String content,
        String attachmentUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static OpinionResponse from(Opinion opinion) {
        return new OpinionResponse(
                opinion.getId(),
                opinion.getProposal().getId(),
                opinion.getUser().getId(),
                opinion.getStance(),
                opinion.getComment(),
                opinion.getAttachmentUrl(),
                opinion.getCreatedAt(),
                opinion.getUpdatedAt()
        );
    }
}
