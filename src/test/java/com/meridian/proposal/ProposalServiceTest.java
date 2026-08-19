package com.meridian.proposal;

import com.meridian.ai.CultureAnalysisRepository;
import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.common.exception.DomainException;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ProposalRepository proposalRepository;
    @Mock
    private ProposalTargetCultureRepository proposalTargetCultureRepository;
    @Mock
    private CultureAnalysisRepository cultureAnalysisRepository;

    private ProposalService proposalService;

    private User author;
    private Team team;

    @BeforeEach
    void setUp() {
        proposalService = new ProposalService(firebaseTokenVerifier, userRepository, teamRepository,
                teamMemberRepository, proposalRepository, proposalTargetCultureRepository, cultureAnalysisRepository);

        author = User.builder().id(1L).firebaseUid("firebase-uid").email("author@example.com").build();
        team = Team.builder().id(10L).name("Design Team").build();

        lenient().when(firebaseTokenVerifier.verify("id-token"))
                .thenReturn(new FirebaseUserClaims("firebase-uid", "author@example.com", "Author", "KR", "Asia/Seoul", null, null));
        lenient().when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(author));
    }

    @Test
    void createsProposalWhenAuthorBelongsToTeam() {
        ProposalCreateRequest request = new ProposalCreateRequest("Title", "Content", 10L, List.of("KR", "US"), List.of(), null);

        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal proposal = invocation.getArgument(0);
            proposal.setId(100L);
            return proposal;
        });
        when(proposalTargetCultureRepository.save(any(ProposalTargetCulture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProposalResponse response = proposalService.createProposal(AUTH_HEADER, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(response.targetCultures()).containsExactly("KR", "US");
    }

    @Test
    void rejectsCreateWhenUserNotTeamMember() {
        ProposalCreateRequest request = new ProposalCreateRequest("Title", "Content", 10L, List.of(), List.of(), null);

        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> proposalService.createProposal(AUTH_HEADER, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("소속된 사용자만");
    }

    @Test
    void rejectsCreateWhenTeamMissing() {
        ProposalCreateRequest request = new ProposalCreateRequest("Title", "Content", 999L, List.of(), List.of(), null);

        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proposalService.createProposal(AUTH_HEADER, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Team not found");
    }

    @Test
    void getProposalAllowsAuthorRegardlessOfStatus() {
        Proposal proposal = draftProposal();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());

        ProposalResponse response = proposalService.getProposal(AUTH_HEADER, 100L);

        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void getProposalRejectsNonMemberOfTargetTeam() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        User other = User.builder().id(2L).firebaseUid("other-uid").build();
        when(firebaseTokenVerifier.verify("id-token"))
                .thenReturn(new FirebaseUserClaims("other-uid", "other@example.com", "Other", null, null, null, null));
        when(userRepository.findByFirebaseUid("other-uid")).thenReturn(Optional.of(other));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> proposalService.getProposal(AUTH_HEADER, 100L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    @Test
    void getProposalRejectsTeamMemberWhileStillDraft() {
        Proposal proposal = draftProposal();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        User other = User.builder().id(2L).firebaseUid("other-uid").build();
        when(firebaseTokenVerifier.verify("id-token"))
                .thenReturn(new FirebaseUserClaims("other-uid", "other@example.com", "Other", null, null, null, null));
        when(userRepository.findByFirebaseUid("other-uid")).thenReturn(Optional.of(other));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> proposalService.getProposal(AUTH_HEADER, 100L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    @Test
    void updateProposalRejectsInvisibleDraftAsNotFound() {
        Proposal proposal = draftProposal();
        proposal.setAuthor(User.builder().id(999L).build());
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);

        assertThatThrownBy(() -> proposalService.updateProposal(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    @Test
    void updateProposalRejectsVisibleNonAuthorAsForbidden() {
        Proposal proposal = draftProposal();
        proposal.setAuthor(User.builder().id(999L).build());
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);

        assertThatThrownBy(() -> proposalService.updateProposal(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_ACCESS_DENIED");
    }

    @Test
    void createProposalRejectsDuplicateTargetCultures() {
        ProposalCreateRequest request = new ProposalCreateRequest("Title", "Content", 10L, List.of("KR", "KR"), List.of(), null);

        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> proposalService.createProposal(AUTH_HEADER, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("DUPLICATE_TARGET_CULTURE");
    }

    @Test
    void updateProposalRejectsNonDraftStatus() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);

        assertThatThrownBy(() -> proposalService.updateProposal(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void deleteProposalRemovesDraftAuthoredByCaller() {
        Proposal proposal = draftProposal();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(cultureAnalysisRepository.findByProposal_Id(100L)).thenReturn(List.of());

        proposalService.deleteProposal(AUTH_HEADER, 100L);
    }

    @Test
    void rejectsMissingBearerToken() {
        assertThatThrownBy(() -> proposalService.listProposals(null))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void publishProposalTransitionsDraftToOpen() {
        Proposal proposal = draftProposal();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());

        ProposalResponse response = proposalService.publishProposal(AUTH_HEADER, 100L);

        assertThat(response.status()).isEqualTo(ProposalStatus.OPEN);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.OPEN);
    }

    @Test
    void publishProposalRejectsAlreadyOpenProposal() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> proposalService.publishProposal(AUTH_HEADER, 100L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_DRAFT");
    }

    @Test
    void publishProposalRejectsOtherNonDraftStatuses() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.CONSENSUS_READY);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> proposalService.publishProposal(AUTH_HEADER, 100L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_DRAFT");
    }

    @Test
    void publishProposalRejectsNonAuthorWithForbidden() {
        Proposal proposal = draftProposal();
        proposal.setAuthor(User.builder().id(999L).build());
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> proposalService.publishProposal(AUTH_HEADER, 100L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_ACCESS_DENIED");
    }

    @Test
    void publishProposalRejectsMissingBearerToken() {
        assertThatThrownBy(() -> proposalService.publishProposal(null, 100L))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void publishProposalRejectsMissingProposal() {
        when(proposalRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proposalService.publishProposal(AUTH_HEADER, 404L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    private Proposal draftProposal() {
        Proposal proposal = Proposal.builder()
                .id(100L)
                .title("Title")
                .content("Content")
                .author(author)
                .targetTeam(team)
                .status(ProposalStatus.DRAFT)
                .build();
        return proposal;
    }
}
