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

/**
 * README §9 AI API. 인증은 UserService를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    // ObjectMapper는 상태 없는(stateless) 유틸리티라 DI 없이 직접 소유한다
    // (Spring Boot의 자동 구성 ObjectMapper 빈 유무에 의존하지 않기 위함).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        userService.getCurrentUserEntity(authorizationHeader);

        CultureAnalysisResult result = cultureAnalysisEngine.analyze(request.originalText(), request.targetCultures());

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
     * 조건을 만족하면 Proposal 상태를 CONSENSUS_READY로 갱신한 뒤(OPEN/IN_PROGRESS일 때만 전진,
     * 이미 CONSENSUS_READY 이상이면 유지) 새 합의 요약 이력을 추가한다.
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
                        .toList()
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

        if (proposal.getStatus() == ProposalStatus.OPEN || proposal.getStatus() == ProposalStatus.IN_PROGRESS) {
            proposal.setStatus(ProposalStatus.CONSENSUS_READY);
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

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI analysis result.", e);
        }
    }
}
