package com.meridian.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.opinion.Opinion;
import com.meridian.opinion.OpinionRepository;
import com.meridian.opinion.OpinionStance;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalRepository;
import com.meridian.proposal.ProposalStatus;
import com.meridian.team.Team;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Firebase / OpenAI 없이, Spring Context + H2 DB로 문화 맥락 분석 API를 end-to-end 검증한다.
 * CultureAnalysisEngine을 mock 처리해 실제 OpenAI API를 호출하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockitoBean
    private CultureAnalysisEngine cultureAnalysisEngine;
    @MockitoBean
    private IntentAnalysisEngine intentAnalysisEngine;
    @MockitoBean
    private ConsensusSummaryEngine consensusSummaryEngine;
    @Autowired
    private CultureAnalysisRepository cultureAnalysisRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private OpinionRepository opinionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        when(firebaseTokenVerifier.verify(eq("id-token")))
                .thenReturn(new FirebaseUserClaims("user-uid", "user@example.com", "User", null, null, null, null));
    }

    @Test
    void contextAnalysisPersistsWithoutProposalAndReturnsEngineResult() throws Exception {
        ContextAnalysisRequest request = new ContextAnalysisRequest(
                "괜찮은 것 같아요. 그런데...", List.of("KR", "US"));
        when(cultureAnalysisEngine.analyze(eq(request.originalText()), eq(request.targetCultures())))
                .thenReturn(new CultureAnalysisResult(
                        RiskLevel.MEDIUM,
                        List.of(new CultureInterpretation("KR", "완곡한 거절로 읽힐 수 있음"),
                                new CultureInterpretation("US", "동의로 오해될 수 있음")),
                        List.of("괜찮은 것 같아요"),
                        "명확하게 동의 또는 거절 의사를 밝혀 주세요."));

        String body = mockMvc.perform(post("/api/ai/context-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalText").value("괜찮은 것 같아요. 그런데..."))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.interpretations[0].culture").value("KR"))
                .andExpect(jsonPath("$.interpretations[0].interpretation").value("완곡한 거절로 읽힐 수 있음"))
                .andExpect(jsonPath("$.interpretations[1].culture").value("US"))
                .andExpect(jsonPath("$.flaggedPhrases[0]").value("괜찮은 것 같아요"))
                .andExpect(jsonPath("$.suggestion").value("명확하게 동의 또는 거절 의사를 밝혀 주세요."))
                .andReturn().getResponse().getContentAsString();

        Long analysisId = objectMapper.readTree(body).get("id").asLong();
        CultureAnalysis saved = cultureAnalysisRepository.findById(analysisId).orElseThrow();
        assertThat(saved.getProposal()).isNull();
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of());

        mockMvc.perform(post("/api/ai/context-analysis")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsBlankOriginalText() throws Exception {
        ContextAnalysisRequest request = new ContextAnalysisRequest("   ", List.of());

        mockMvc.perform(post("/api/ai/context-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void intentAnalysisReturnsEngineResultAndDoesNotPersist() throws Exception {
        long countBefore = cultureAnalysisRepository.count();

        IntentAnalysisRequest request = new IntentAnalysisRequest("괜찮은 것 같아요. 다만 일정이 조금 걱정되네요.");
        when(intentAnalysisEngine.analyze(eq(request.content())))
                .thenReturn(new IntentAnalysisResult("긍정", "일정 측면에서 조건부 반대 또는 우려 가능성"));

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.surfaceOpinion").value("긍정"))
                .andExpect(jsonPath("$.potentialOpinion").value("일정 측면에서 조건부 반대 또는 우려 가능성"));

        assertThat(cultureAnalysisRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void intentAnalysisRejectsRequestWithoutBearerToken() throws Exception {
        IntentAnalysisRequest request = new IntentAnalysisRequest("원문");

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void intentAnalysisRejectsBlankContent() throws Exception {
        IntentAnalysisRequest request = new IntentAnalysisRequest("   ");

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void consensusSummaryPersistsAndAdvancesProposalToConsensusReady() throws Exception {
        Team team = teamRepository.save(Team.builder().name("Design Team").country("KR").build());
        User author = userRepository.save(User.builder().firebaseUid("user-uid").email("user@example.com").name("Author").build());
        User member = userRepository.save(User.builder().firebaseUid("member-uid").email("member@example.com").name("Member").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(author).role("PM").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(member).role("MEMBER").build());

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .title("디자인 시안 B 적용")
                .content("이번 프로젝트의 메인 디자인으로 B안을 적용하는 것은 어떨까요?")
                .author(author)
                .targetTeam(team)
                .status(ProposalStatus.OPEN)
                .build());
        opinionRepository.save(Opinion.builder().proposal(proposal).user(author).stance(OpinionStance.AGREE).comment("찬성합니다.").build());
        opinionRepository.save(Opinion.builder().proposal(proposal).user(member).stance(OpinionStance.CONDITIONAL_AGREE).comment("조건부로 찬성합니다.").build());

        when(consensusSummaryEngine.analyze(eq(proposal.getTitle()), eq(proposal.getContent()), any())).thenReturn(
                new ConsensusAnalysisResult(ConsensusStatus.PARTIAL, "부분 합의", List.of("일정"),
                        List.of("문화적 해석 차이"), List.of("완곡한 반대"), "추가 논의가 필요합니다."));

        mockMvc.perform(post("/api/ai/consensus-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConsensusSummaryRequest(proposal.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consensusStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.keyIssues[0]").value("일정"))
                .andExpect(jsonPath("$.hiddenOpposition[0]").value("완곡한 반대"));

        Proposal updated = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProposalStatus.CONSENSUS_READY);
    }

    @Test
    void consensusSummaryRejectsWhenResponsesIncompleteAndNoDeadline() throws Exception {
        Team team = teamRepository.save(Team.builder().name("Design Team").country("KR").build());
        User author = userRepository.save(User.builder().firebaseUid("user-uid").email("user@example.com").name("Author").build());
        User member = userRepository.save(User.builder().firebaseUid("member-uid").email("member@example.com").name("Member").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(author).role("PM").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(member).role("MEMBER").build());

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team).status(ProposalStatus.OPEN).build());
        opinionRepository.save(Opinion.builder().proposal(proposal).user(author).stance(OpinionStance.AGREE).comment("찬성합니다.").build());

        mockMvc.perform(post("/api/ai/consensus-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConsensusSummaryRequest(proposal.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_RESPONSES"));
    }
}
