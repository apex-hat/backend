package com.meridian.opinion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.common.exception.GlobalExceptionHandler;
import com.meridian.common.response.ErrorResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Firebase 없이, Spring Context + H2 DB로 Opinion API 4종을 end-to-end 검증한다.
 * FirebaseTokenVerifier만 목(mock) 처리하고 나머지는 실제 Controller-Service-Repository-JPA 경로를 그대로 탄다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpinionApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private OpinionRepository opinionRepository;
    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    private Team team;
    private User author;
    private User member;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        team = teamRepository.save(Team.builder().name("Design Team").country("KR").build());

        author = userRepository.save(User.builder().firebaseUid("author-uid").email("author@example.com").name("Author").build());
        member = userRepository.save(User.builder().firebaseUid("member-uid").email("member@example.com").name("Member").build());
        userRepository.save(User.builder().firebaseUid("stranger-uid").email("stranger@example.com").name("Stranger").build());

        teamMemberRepository.save(TeamMember.builder().team(team).user(author).role("PM").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(member).role("MEMBER").build());

        stubToken("author-token", "author-uid", "author@example.com");
        stubToken("member-token", "member-uid", "member@example.com");
        stubToken("stranger-token", "stranger-uid", "stranger@example.com");
    }

    @Test
    void fullOpinionLifecycle_createTransitionsProposalListUpdateDelete() throws Exception {
        Proposal proposal = openProposal();

        String createBody = mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new OpinionRequest(OpinionStance.CONDITIONAL_AGREE, "동의하지만 모바일은 추가 검토가 필요합니다.", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("동의하지만 모바일은 추가 검토가 필요합니다."))
                .andReturn().getResponse().getContentAsString();
        Long opinionId = objectMapper.readTree(createBody).get("id").asLong();

        // README §14: 첫 의견 등록으로 OPEN -> IN_PROGRESS 자동 전이
        Proposal reloaded = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProposalStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(opinionId));

        mockMvc.perform(put("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new OpinionRequest(OpinionStance.AGREE, "재검토 후 동의로 변경합니다.", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stance").value("AGREE"))
                .andExpect(jsonPath("$.content").value("재검토 후 동의로 변경합니다."));

        mockMvc.perform(delete("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token"))
                .andExpect(status().isNoContent());

        assertThat(opinionRepository.findById(opinionId)).isEmpty();
    }

    @Test
    void nonTeamMemberCannotCreateOrListOpinions() throws Exception {
        Proposal proposal = openProposal();

        mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));

        mockMvc.perform(get("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOpinionOnDraftProposal() throws Exception {
        Proposal draft = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team)
                .status(ProposalStatus.DRAFT).build());

        mockMvc.perform(post("/api/proposals/" + draft.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_ACCEPTING_OPINIONS"));
    }

    @Test
    void rejectsOpinionOnCompletedProposal() throws Exception {
        Proposal completed = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team)
                .status(ProposalStatus.COMPLETED).build());

        mockMvc.perform(post("/api/proposals/" + completed.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_ACCEPTING_OPINIONS"));
    }

    @Test
    void rejectsDuplicateOpinionViaApi() throws Exception {
        Proposal proposal = openProposal();
        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "동의합니다.", null);

        mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPINION_ALREADY_EXISTS"));
    }

    @Test
    void nonOwnerNonPmCannotUpdateOrDeleteOthersOpinion() throws Exception {
        Proposal proposal = openProposal();
        String body = mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andReturn().getResponse().getContentAsString();
        Long opinionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.DISAGREE, "가로채기 시도", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OPINION_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token"))
                .andExpect(status().isForbidden());
    }

    /** PM은 팀 진행 관리 목적으로 다른 팀원의 의견도 수정/삭제할 수 있다(모더레이션). */
    @Test
    void teamPmCanUpdateAndDeleteOthersOpinion() throws Exception {
        Proposal proposal = openProposal();
        String body = mockMvc.perform(post("/api/proposals/" + proposal.getId() + "/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andReturn().getResponse().getContentAsString();
        Long opinionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.DISAGREE, "PM 모더레이션", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("PM 모더레이션"));

        mockMvc.perform(delete("/api/opinions/" + opinionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isNoContent());
    }

    /**
     * (proposal_id, user_id) UNIQUE 제약이 애플리케이션 사전 검증과 별개로 DB 레벨에서도
     * 실제로 걸리는지, 그리고 DataIntegrityViolationException이 기존 GlobalExceptionHandler를 통해
     * 409/DATA_CONFLICT로 매핑되는지 확인한다. 서비스의 existsBy 사전 검증을 우회하기 위해
     * Repository를 직접 호출한다(동시성 레이스로만 재현되는 경로를 결정적으로 재현).
     */
    @Test
    void databaseUniqueConstraintBacksUpApplicationCheckAndMapsToDataConflict() {
        Proposal proposal = openProposal();

        opinionRepository.saveAndFlush(Opinion.builder()
                .proposal(proposal).user(member).stance(OpinionStance.AGREE).comment("첫 의견").build());

        Opinion duplicate = Opinion.builder()
                .proposal(proposal).user(member).stance(OpinionStance.DISAGREE).comment("중복 의견").build();

        assertThatThrownBy(() -> opinionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key: (proposal_id, user_id)"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("DATA_CONFLICT");
    }

    private Proposal openProposal() {
        return proposalRepository.save(Proposal.builder()
                .title("디자인 시안 B 적용").content("이번 프로젝트의 메인 디자인으로 B안을 적용하는 것은 어떨까요?")
                .author(author).targetTeam(team).status(ProposalStatus.OPEN).build());
    }

    private void stubToken(String token, String uid, String email) {
        when(firebaseTokenVerifier.verify(eq(token)))
                .thenReturn(new FirebaseUserClaims(uid, email, uid, null, null, null, null));
    }
}
