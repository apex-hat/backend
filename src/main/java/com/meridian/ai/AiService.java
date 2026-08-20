package com.meridian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.common.exception.DomainException;
import com.meridian.opinion.Opinion;
import com.meridian.opinion.OpinionRepository;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.proposal.ProposalStatus;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * README §9 AI API. 인증은 UserService를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    // ObjectMapper는 상태 없는(stateless) 유틸리티라 DI 없이 직접 소유한다
    // (Spring Boot의 자동 구성 ObjectMapper 빈 유무에 의존하지 않기 위함).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Backend User에는 별도 preferred_language 필드가 없어(README §13.1), 프로필의 country로
    // 언어를 추정한다. 프론트 ProfilePage.tsx의 COUNTRY_LANGUAGE 매핑과 동일하게 맞춘다.
    private static final Map<String, String> COUNTRY_LANGUAGE = Map.of(
            "KR", "Korean",
            "US", "English",
            "JP", "Japanese",
            "IN", "English",
            "SG", "English",
            "GB", "English",
            "DE", "German",
            "FR", "English",
            "BR", "Portuguese",
            "AU", "English"
    );
    private static final String DEFAULT_LANGUAGE = "English";

    private final UserService userService;
    private final CultureAnalysisEngine cultureAnalysisEngine;
    private final CultureAnalysisRepository cultureAnalysisRepository;
    private final IntentAnalysisEngine intentAnalysisEngine;
    private final ConsensusSummaryEngine consensusSummaryEngine;
    private final ConsensusSummaryRepository consensusSummaryRepository;
    private final ProposalService proposalService;
    private final OpinionRepository opinionRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional
    public ContextAnalysisResponse contextAnalysis(String authorizationHeader, ContextAnalysisRequest request) {
        User user = userService.getCurrentUserEntity(authorizationHeader);

        CultureAnalysisResult result = cultureAnalysisEngine.analyze(
                request.originalText(), request.targetCultures(), resolveLanguage(user.getCountry()));

        CultureAnalysis analysis = cultureAnalysisRepository.save(CultureAnalysis.builder()
                .originalText(request.originalText())
                .riskLevel(result.riskLevel())
                .interpretation(writeJson(result.interpretations()))
                .flaggedPhrase(writeJson(result.flaggedPhrases()))
                .suggestedRewrite(result.suggestion())
                .build());

        return ContextAnalysisResponse.from(analysis, OBJECT_MAPPER);
    }

    /**
     * README §9.2 AI 합의 요약. 대상 팀원 전원이 응답했거나 deadline이 지났을 때만 실행할 수 있고,
     * 실행에 성공하면 Proposal 상태를 CONSENSUS_COMPLETED(분석 완료)로 갱신한 뒤(COMPLETED로
     * 이미 넘어갔으면 유지) 새 합의 요약 이력을 추가한다. CONSENSUS_READY(분석 가능)는 이 API가
     * 아니라 팀원 전원 응답 시점에 OpinionService가 먼저 전이시킨다.
     */
    @Transactional
    public ConsensusSummaryResponse consensusSummary(String authorizationHeader, ConsensusSummaryRequest request) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Proposal proposal = proposalService.getVisibleProposal(request.proposalId(), user);

        List<Opinion> opinions = opinionRepository.findAllByProposal_Id(proposal.getId());
        if (opinions.isEmpty()) {
            throw DomainException.conflict("INSUFFICIENT_RESPONSES", "등록된 의견이 없어 합의 요약을 실행할 수 없습니다.");
        }

        int totalMembers = teamMemberRepository.findAllByTeam_Id(proposal.getTargetTeam().getId()).size();
        boolean allMembersResponded = totalMembers > 0 && opinions.size() >= totalMembers;
        boolean deadlinePassed = proposal.getDeadline() != null && Instant.now().isAfter(proposal.getDeadline());
        if (!allMembersResponded && !deadlinePassed) {
            throw DomainException.conflict("INSUFFICIENT_RESPONSES",
                    "아직 응답하지 않은 팀원이 있고 마감 기한도 지나지 않아 합의 요약을 실행할 수 없습니다.");
        }

        ConsensusAnalysisResult result = consensusSummaryEngine.analyze(
                proposal.getTitle(),
                proposal.getContent(),
                opinions.stream()
                        .map(opinion -> new ConsensusOpinionInput(opinion.getStance().name(), opinion.getComment()))
                        .toList(),
                resolveLanguage(user.getCountry())
        );

        ConsensusSummary saved = consensusSummaryRepository.save(ConsensusSummary.builder()
                .proposal(proposal)
                .consensusStatus(result.consensusStatus())
                .summary(result.summary())
                .keyIssues(writeJson(result.keyIssues()))
                .culturalAnalysis(writeJson(result.culturalAnalysis()))
                .hiddenOpposition(writeJson(result.hiddenOpposition()))
                .recommendedActions(result.recommendedActions())
                .build());

        if (proposal.getStatus() != ProposalStatus.COMPLETED) {
            proposal.setStatus(ProposalStatus.CONSENSUS_COMPLETED);
        }

        return ConsensusSummaryResponse.from(saved, OBJECT_MAPPER);
    }

    /**
     * README §9.3 숨은 의도 분석. DB에 저장하지 않는다 — ERD(§13.1)에 저장 테이블이 없음.
     */
    public IntentAnalysisResponse intentAnalysis(String authorizationHeader, IntentAnalysisRequest request) {
        userService.getCurrentUserEntity(authorizationHeader);

        IntentAnalysisResult result = intentAnalysisEngine.analyze(request.content());

        return IntentAnalysisResponse.from(request.content(), result);
    }

    private String resolveLanguage(String country) {
        return country == null ? DEFAULT_LANGUAGE : COUNTRY_LANGUAGE.getOrDefault(country, DEFAULT_LANGUAGE);
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI analysis result.", e);
        }
    }
}
