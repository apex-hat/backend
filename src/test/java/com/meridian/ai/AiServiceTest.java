package com.meridian.ai;

import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
import com.meridian.opinion.Opinion;
import com.meridian.opinion.OpinionRepository;
import com.meridian.opinion.OpinionStance;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.proposal.ProposalStatus;
import com.meridian.team.Team;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private CultureAnalysisEngine cultureAnalysisEngine;
    @Mock
    private CultureAnalysisRepository cultureAnalysisRepository;
    @Mock
    private IntentAnalysisEngine intentAnalysisEngine;
    @Mock
    private ConsensusSummaryEngine consensusSummaryEngine;
    @Mock
    private ConsensusSummaryRepository consensusSummaryRepository;
    @Mock
    private ProposalService proposalService;
    @Mock
    private OpinionRepository opinionRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    private AiService aiService;
    private User author;
    private Team team;

    @BeforeEach
    void setUp() {
        aiService = new AiService(userService, cultureAnalysisEngine, cultureAnalysisRepository, intentAnalysisEngine,
                consensusSummaryEngine, consensusSummaryRepository, proposalService, opinionRepository, teamMemberRepository);
        author = User.builder().id(1L).build();
        team = Team.builder().id(10L).name("Design Team").build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(author);
    }

    @Test
    void persistsAndReturnsEngineResult() {
        ContextAnalysisRequest request = new ContextAnalysisRequest(
                "괜찮은 것 같아요. 그런데...", List.of("KR", "US"));

        CultureAnalysisResult engineResult = new CultureAnalysisResult(
                RiskLevel.HIGH,
                List.of(new CultureInterpretation("KR", "완곡한 반대 또는 우려로 해석될 가능성이 있음"),
                        new CultureInterpretation("US", "기본적으로 긍정적인 의견으로 해석될 가능성이 있음")),
                List.of("괜찮은 것 같아요"),
                "전체적으로 긍정적이지만 일정 부분 수정이 필요하다고 생각합니다.");
        when(cultureAnalysisEngine.analyze("괜찮은 것 같아요. 그런데...", List.of("KR", "US"))).thenReturn(engineResult);
        when(cultureAnalysisRepository.save(any(CultureAnalysis.class))).thenAnswer(invocation -> {
            CultureAnalysis analysis = invocation.getArgument(0);
            analysis.setId(1L);
            return analysis;
        });

        ContextAnalysisResponse response = aiService.contextAnalysis(AUTH_HEADER, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.originalText()).isEqualTo("괜찮은 것 같아요. 그런데...");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.interpretations()).containsExactly(
                new CultureInterpretation("KR", "완곡한 반대 또는 우려로 해석될 가능성이 있음"),
                new CultureInterpretation("US", "기본적으로 긍정적인 의견으로 해석될 가능성이 있음"));
        assertThat(response.flaggedPhrases()).containsExactly("괜찮은 것 같아요");
        assertThat(response.suggestion()).isEqualTo("전체적으로 긍정적이지만 일정 부분 수정이 필요하다고 생각합니다.");
    }

    @Test
    void savesProposalIdAsNullForPreRegistrationAnalysis() {
        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of("KR"));
        when(cultureAnalysisEngine.analyze("원문", List.of("KR")))
                .thenReturn(new CultureAnalysisResult(RiskLevel.LOW, List.of(), List.of(), "원문"));
        when(cultureAnalysisRepository.save(any(CultureAnalysis.class))).thenAnswer(invocation -> {
            CultureAnalysis analysis = invocation.getArgument(0);
            assertThat(analysis.getProposal()).isNull();
            analysis.setId(1L);
            return analysis;
        });

        aiService.contextAnalysis(AUTH_HEADER, request);
    }

    @Test
    void rejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of());

        assertThatThrownBy(() -> aiService.contextAnalysis(null, request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void intentAnalysisReturnsEngineResultWithoutPersisting() {
        IntentAnalysisRequest request = new IntentAnalysisRequest("괜찮은 것 같아요. 다만 일정이 조금 걱정되네요.");
        when(intentAnalysisEngine.analyze(request.content()))
                .thenReturn(new IntentAnalysisResult("긍정", "일정 측면에서 조건부 반대 또는 우려 가능성"));

        IntentAnalysisResponse response = aiService.intentAnalysis(AUTH_HEADER, request);

        assertThat(response.content()).isEqualTo(request.content());
        assertThat(response.surfaceOpinion()).isEqualTo("긍정");
        assertThat(response.potentialOpinion()).isEqualTo("일정 측면에서 조건부 반대 또는 우려 가능성");
    }

    @Test
    void intentAnalysisRejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        IntentAnalysisRequest request = new IntentAnalysisRequest("원문");

        assertThatThrownBy(() -> aiService.intentAnalysis(null, request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void consensusSummaryPersistsResultAndAdvancesStatusWhenAllMembersResponded() {
        Proposal proposal = openProposal(null);
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(opinionRepository.findAllByProposal_Id(proposal.getId())).thenReturn(List.of(
                opinion(proposal, OpinionStance.AGREE, "찬성합니다."),
                opinion(proposal, OpinionStance.CONDITIONAL_AGREE, "조건부로 찬성합니다.")));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().team(team).user(author).role("PM").build(),
                TeamMember.builder().team(team).user(User.builder().id(2L).build()).role("MEMBER").build()));

        ConsensusAnalysisResult engineResult = new ConsensusAnalysisResult(
                ConsensusStatus.PARTIAL, "부분 합의", List.of("일정"), List.of("문화적 해석 차이"),
                List.of("완곡한 반대"), "추가 논의 필요");
        when(consensusSummaryEngine.analyze(any(), any(), any())).thenReturn(engineResult);
        when(consensusSummaryRepository.save(any(ConsensusSummary.class))).thenAnswer(invocation -> {
            ConsensusSummary summary = invocation.getArgument(0);
            summary.setId(1L);
            return summary;
        });

        ConsensusSummaryResponse response = aiService.consensusSummary(AUTH_HEADER, new ConsensusSummaryRequest(100L));

        assertThat(response.consensusStatus()).isEqualTo(ConsensusStatus.PARTIAL);
        assertThat(response.keyIssues()).containsExactly("일정");
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CONSENSUS_READY);
    }

    @Test
    void consensusSummaryAllowedWhenDeadlinePassedEvenIfIncomplete() {
        Proposal proposal = openProposal(Instant.now().minus(1, ChronoUnit.DAYS));
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(opinionRepository.findAllByProposal_Id(proposal.getId())).thenReturn(List.of(
                opinion(proposal, OpinionStance.DISAGREE, "일정이 촉박합니다.")));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().team(team).user(author).role("PM").build(),
                TeamMember.builder().team(team).user(User.builder().id(2L).build()).role("MEMBER").build(),
                TeamMember.builder().team(team).user(User.builder().id(3L).build()).role("MEMBER").build()));
        when(consensusSummaryEngine.analyze(any(), any(), any())).thenReturn(
                new ConsensusAnalysisResult(ConsensusStatus.DISAGREED, "이견 있음", List.of(), List.of(), List.of(), "재논의 필요"));
        when(consensusSummaryRepository.save(any(ConsensusSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConsensusSummaryResponse response = aiService.consensusSummary(AUTH_HEADER, new ConsensusSummaryRequest(100L));

        assertThat(response.consensusStatus()).isEqualTo(ConsensusStatus.DISAGREED);
    }

    @Test
    void consensusSummaryRejectsWhenResponsesIncompleteAndDeadlineNotPassed() {
        Proposal proposal = openProposal(Instant.now().plus(1, ChronoUnit.DAYS));
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(opinionRepository.findAllByProposal_Id(proposal.getId())).thenReturn(List.of(
                opinion(proposal, OpinionStance.AGREE, "찬성합니다.")));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().team(team).user(author).role("PM").build(),
                TeamMember.builder().team(team).user(User.builder().id(2L).build()).role("MEMBER").build()));

        assertThatThrownBy(() -> aiService.consensusSummary(AUTH_HEADER, new ConsensusSummaryRequest(100L)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("INSUFFICIENT_RESPONSES");
    }

    @Test
    void consensusSummaryRejectsWhenNoOpinionsYet() {
        Proposal proposal = openProposal(null);
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(opinionRepository.findAllByProposal_Id(proposal.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> aiService.consensusSummary(AUTH_HEADER, new ConsensusSummaryRequest(100L)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("INSUFFICIENT_RESPONSES");
    }

    private Proposal openProposal(Instant deadline) {
        return Proposal.builder()
                .id(100L)
                .title("디자인 시안 B 적용")
                .content("이번 프로젝트의 메인 디자인으로 B안을 적용하는 것은 어떨까요?")
                .author(author)
                .targetTeam(team)
                .status(ProposalStatus.OPEN)
                .deadline(deadline)
                .build();
    }

    private Opinion opinion(Proposal proposal, OpinionStance stance, String comment) {
        return Opinion.builder()
                .proposal(proposal)
                .user(author)
                .stance(stance)
                .comment(comment)
                .build();
    }
}
