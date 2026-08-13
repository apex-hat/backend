package com.meridian.proposal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Firebase / 외부 서버 없이, Spring Context + H2 DB로 Proposal API 5종을 end-to-end 검증한다.
 * FirebaseTokenVerifier만 목(mock) 처리하고 나머지는 실제 Controller-Service-Repository-JPA 경로를 그대로 탄다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalApiIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    private Team team;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        team = teamRepository.save(Team.builder().name("Design Team").country("KR").build());

        User author = userRepository.save(User.builder().firebaseUid("author-uid").email("author@example.com").name("Author").build());
        User member = userRepository.save(User.builder().firebaseUid("member-uid").email("member@example.com").name("Member").build());
        userRepository.save(User.builder().firebaseUid("stranger-uid").email("stranger@example.com").name("Stranger").build());

        teamMemberRepository.save(TeamMember.builder().team(team).user(author).role("PM").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(member).role("MEMBER").build());

        stubToken("author-token", "author-uid", "author@example.com");
        stubToken("member-token", "member-uid", "member@example.com");
        stubToken("stranger-token", "stranger-uid", "stranger@example.com");
    }

    @Test
    void fullDraftLifecycle_createReadUpdateDelete() throws Exception {
        ProposalCreateRequest createRequest = new ProposalCreateRequest(
                "디자인 시안 B 적용", "이번 프로젝트의 메인 디자인으로 B안을 적용하는 것은 어떨까요?",
                team.getId(), List.of("KR", "US", "IN"), List.of(), null);

        String createResponseBody = mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.targetCultures[0]").value("KR"))
                .andReturn().getResponse().getContentAsString();

        Long proposalId = objectMapper.readTree(createResponseBody).get("id").asLong();

        // 작성자는 DRAFT 상태에서도 상세 조회 가능
        mockMvc.perform(get("/api/proposals/" + proposalId).header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("디자인 시안 B 적용"));

        // 같은 팀원이지만 DRAFT 상태라 존재 자체가 노출되지 않음 (404)
        mockMvc.perform(get("/api/proposals/" + proposalId).header(HttpHeaders.AUTHORIZATION, "Bearer member-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));

        // 작성자 목록 조회에는 노출됨
        mockMvc.perform(get("/api/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(proposalId));

        // 수정
        ProposalUpdateRequest updateRequest = new ProposalUpdateRequest(
                "디자인 시안 B 최종 적용", "일정 우려를 반영해 모바일 UI는 추가 논의", List.of("KR", "BR"), null);
        mockMvc.perform(put("/api/proposals/" + proposalId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("디자인 시안 B 최종 적용"))
                .andExpect(jsonPath("$.targetCultures[1]").value("BR"));

        // 삭제
        mockMvc.perform(delete("/api/proposals/" + proposalId).header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/proposals/" + proposalId).header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));
    }

    @Test
    void nonTeamMemberCannotCreateProposalForTeam() throws Exception {
        ProposalCreateRequest createRequest = new ProposalCreateRequest(
                "제목", "내용", team.getId(), List.of(), List.of(), null);

        mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_ACCESS_DENIED"));
    }

    @Test
    void nonAuthorTeamMemberCannotSeeOrTouchDraftProposal() throws Exception {
        ProposalCreateRequest createRequest = new ProposalCreateRequest(
                "제목", "내용", team.getId(), List.of(), List.of(), null);
        String body = mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long proposalId = objectMapper.readTree(body).get("id").asLong();

        // DRAFT는 팀원에게도 존재를 숨기므로 수정/삭제 시도 역시 404
        ProposalUpdateRequest updateRequest = new ProposalUpdateRequest("변경", "변경 내용", List.of(), null);
        mockMvc.perform(put("/api/proposals/" + proposalId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/proposals/" + proposalId).header(HttpHeaders.AUTHORIZATION, "Bearer member-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAuthorTeamMemberCannotUpdateOrDeletePublishedProposal() throws Exception {
        // publish API는 별도 담당 범위라, 게시된 상태를 리포지토리로 직접 세팅해 시나리오를 재현한다.
        User author = userRepository.findByFirebaseUid("author-uid").orElseThrow();
        Proposal published = proposalRepository.save(Proposal.builder()
                .title("게시된 제안")
                .content("내용")
                .author(author)
                .targetTeam(team)
                .status(ProposalStatus.OPEN)
                .build());

        ProposalUpdateRequest updateRequest = new ProposalUpdateRequest("변경", "변경 내용", List.of(), null);
        mockMvc.perform(put("/api/proposals/" + published.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer member-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/proposals/" + published.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer member-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDuplicateTargetCulturesWithClearBadRequest() throws Exception {
        ProposalCreateRequest createRequest = new ProposalCreateRequest(
                "제목", "내용", team.getId(), List.of("KR", "KR"), List.of(), null);

        mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_TARGET_CULTURE"));
    }

    @Test
    void rejectsBlankTitleWithValidationError() throws Exception {
        ProposalCreateRequest createRequest = new ProposalCreateRequest(
                "  ", "내용", team.getId(), List.of(), List.of(), null);

        mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private void stubToken(String token, String uid, String email) {
        when(firebaseTokenVerifier.verify(eq(token)))
                .thenReturn(new FirebaseUserClaims(uid, email, uid, null, null, null, null));
    }
}
