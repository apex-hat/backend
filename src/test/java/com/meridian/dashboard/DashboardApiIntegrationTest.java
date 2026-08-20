package com.meridian.dashboard;

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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Firebase 없이, Spring Context + H2 DB로 Dashboard API 2종을 end-to-end 검증한다.
 * FirebaseTokenVerifier만 목(mock) 처리하고 나머지는 실제 Controller-Service-Repository-JPA 경로를 그대로 탄다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardApiIntegrationTest {

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

    private MockMvc mockMvc;

    private Team team;
    private User author;
    private User member;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        team = teamRepository.save(Team.builder().name("Design Team").country("KR").build());

        author = userRepository.save(User.builder().firebaseUid("author-uid").email("author@example.com").name("Author")
                .country("KR").timeZone("Asia/Seoul").location("Seoul HQ").build());
        member = userRepository.save(User.builder().firebaseUid("member-uid").email("member@example.com").name("Member")
                .country("US").timeZone("America/New_York").build());
        userRepository.save(User.builder().firebaseUid("stranger-uid").email("stranger@example.com").name("Stranger").build());

        teamMemberRepository.save(TeamMember.builder().team(team).user(author).role("PM").build());
        teamMemberRepository.save(TeamMember.builder().team(team).user(member).role("MEMBER").build());

        stubToken("author-token", "author-uid", "author@example.com");
        stubToken("member-token", "member-uid", "member@example.com");
        stubToken("stranger-token", "stranger-uid", "stranger@example.com");
    }

    @Test
    void timezonesReturnsTeamMembers() throws Exception {
        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", team.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[?(@.userId == " + author.getId() + ")].country").value("KR"))
                .andExpect(jsonPath("$.members[?(@.userId == " + author.getId() + ")].localTime").exists());
    }

    @Test
    void timezonesRejectsNonTeamMember() throws Exception {
        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", team.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_ACCESS_DENIED"));
    }

    @Test
    void timezonesRejectsNonExistentTeam() throws Exception {
        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void timezonesRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", team.getId().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void statusReflectsGrowingResponses() throws Exception {
        Proposal proposal = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team).status(ProposalStatus.OPEN).build());

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", proposal.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMembers").value(2))
                .andExpect(jsonPath("$.respondedMembers").value(0))
                .andExpect(jsonPath("$.responseRate").value(0))
                .andExpect(jsonPath("$.status").value("OPEN"));

        opinionRepository.save(Opinion.builder().proposal(proposal).user(member)
                .stance(OpinionStance.AGREE).comment("동의합니다.").build());

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", proposal.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respondedMembers").value(1))
                .andExpect(jsonPath("$.responseRate").value(50));

        opinionRepository.save(Opinion.builder().proposal(proposal).user(author)
                .stance(OpinionStance.CONDITIONAL_AGREE).comment("조건부 동의.").build());

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", proposal.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respondedMembers").value(2))
                .andExpect(jsonPath("$.responseRate").value(100));
    }

    @Test
    void statusRejectsInaccessibleProposalWith404() throws Exception {
        Proposal draft = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team).status(ProposalStatus.DRAFT).build());

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", draft.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stranger-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));
    }

    @Test
    void statusRejectsNonExistentProposal() throws Exception {
        mockMvc.perform(get("/api/dashboard/status").param("proposalId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer author-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));
    }

    @Test
    void statusRejectsMissingBearerToken() throws Exception {
        Proposal proposal = proposalRepository.save(Proposal.builder()
                .title("제목").content("내용").author(author).targetTeam(team).status(ProposalStatus.OPEN).build());

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", proposal.getId().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private void stubToken(String token, String uid, String email) {
        when(firebaseTokenVerifier.verify(eq(token)))
                .thenReturn(new FirebaseUserClaims(uid, email, uid, null, null, null, null));
    }
}
