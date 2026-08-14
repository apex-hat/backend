package com.meridian.opinion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * README §8 의견 등록/수정 요청 Body. JSON 필드명은 README §8 예시를 따라 {@code content}를 쓰고,
 * entity({@link Opinion#getComment()})와의 매핑은 {@link OpinionService}에서 명시적으로 처리한다.
 */
public record OpinionRequest(
        @NotNull OpinionStance stance,
        @NotBlank String content,
        String attachmentUrl
) {
}
