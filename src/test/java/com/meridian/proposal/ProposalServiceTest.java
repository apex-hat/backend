package com.meridian.proposal;

import com.meridian.activity.ActivityAction;
import com.meridian.activity.ActivityLogService;
import com.meridian.ai.CultureAnalysisRepository;
import com.meridian.ai.ConsensusSummaryRepository;
import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.common.exception.DomainException;
import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.opinion.OpinionRepository;
import com.meridian.realtime.TeamEventPublisher;
import com.meridian.team.Team;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberId;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Mock
    private OpinionRepository opinionRepository;
    @Mock
    private ConsensusSummaryRepository consensusSummaryRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TeamEventPublisher teamEventPublisher;
    @Mock
    private ActivityLogService activityLogService;

    private ProposalService proposalService;

    private User author;
    private Team team;

    @BeforeEach
    void setUp() {
        proposalService = new ProposalService(firebaseTokenVerifier, userRepository, teamRepository,
                teamMemberRepository, proposalRepository, proposalTargetCultureRepository, cultureAnalysisRepository,
                opinionRepository, consensusSummaryRepository, notificationRepository, teamEventPublisher, activityLogService);

        author = User.builder().id(1L).firebaseUid("firebase-uid").email("author@example.com").name("Author").build();
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
    void updateProposalAllowedForTeamPmNonAuthorAndNotifiesOriginalAuthor() {
        User teammate = User.builder().id(2L).name("Teammate").build();
        Proposal proposal = draftProposal();
        proposal.setAuthor(teammate);
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        // 행위자(PM, id=1)도 실제로는 팀원이므로 목록에 포함시켜, PM이 본인 행위에 대한
        // 알림을 받지 않는지(자기 알림 배제)까지 검증한다.
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().id(new TeamMemberId(10L, 1L)).team(team).user(author).role("PM").build(),
                TeamMember.builder().id(new TeamMemberId(10L, 2L)).team(team).user(teammate).role("MEMBER").build()));

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);
        ProposalResponse response = proposalService.updateProposal(AUTH_HEADER, 100L, request);

        assertThat(response.title()).isEqualTo("New");
        verify(activityLogService).record(eq(team), eq(author), eq(teammate),
                eq(ActivityAction.PROPOSAL_UPDATED_BY_PM), any());

        // 팀 전체 알림(PROPOSAL_UPDATED)과 PM 모더레이션 알림(PROPOSAL_UPDATED_BY_PM)이 각각 1건씩,
        // 총 2건 모두 원 작성자(teammate)에게만 가야 한다 — 행위자인 PM(author) 자신은 제외되어야 한다.
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getUser)
                .containsOnly(teammate);
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.PROPOSAL_UPDATED, NotificationType.PROPOSAL_UPDATED_BY_PM);
    }

    @Test
    void deleteProposalAllowedForTeamPmNonAuthorAndNotifiesOriginalAuthor() {
        User teammate = User.builder().id(2L).name("Teammate").build();
        Proposal proposal = draftProposal();
        proposal.setAuthor(teammate);
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(cultureAnalysisRepository.findByProposal_Id(100L)).thenReturn(List.of());

        proposalService.deleteProposal(AUTH_HEADER, 100L);

        verify(activityLogService).record(eq(team), eq(author), eq(teammate),
                eq(ActivityAction.PROPOSAL_DELETED_BY_PM), any());
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(teammate);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.PROPOSAL_DELETED_BY_PM);
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
    void updateProposalAllowsPublishedActiveStatus() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.OPEN);
        User teammate = User.builder().id(2L).name("Teammate").build();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().id(new TeamMemberId(10L, 1L)).team(team).user(author).role("PM").build(),
                TeamMember.builder().id(new TeamMemberId(10L, 2L)).team(team).user(teammate).role("MEMBER").build()));

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);

        ProposalResponse response = proposalService.updateProposal(AUTH_HEADER, 100L, request);

        assertThat(response.title()).isEqualTo("New");

        // 이미 게시되어 팀원에게 보이는 제안이 수정되었으므로, 작성자를 제외한 팀원에게 알림을 보낸다.
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(teammate);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.PROPOSAL_UPDATED);
    }

    @Test
    void updateProposalDoesNotNotifyWhileStillDraft() {
        Proposal proposal = draftProposal();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of(), null);

        proposalService.updateProposal(AUTH_HEADER, 100L, request);

        // DRAFT는 팀원에게 보이지 않으므로 알림을 보내지 않는다.
        verify(notificationRepository, never()).save(any(Notification.class));
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
        assertThatThrownBy(() -> proposalService.listProposals(null, null))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void listProposalsWithoutTeamIdAggregatesAllOfAuthorsTeams() {
        Proposal proposal = draftProposal();
        when(teamMemberRepository.findAllByUser_Id(1L)).thenReturn(List.of(
                TeamMember.builder().id(new TeamMemberId(10L, 1L)).team(team).user(author).role("PM").build()));
        when(proposalRepository.findVisibleToUser(1L, List.of(10L))).thenReturn(List.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());

        List<ProposalResponse> responses = proposalService.listProposals(AUTH_HEADER, null);

        assertThat(responses).extracting(ProposalResponse::id).containsExactly(100L);
    }

    @Test
    void listProposalsWithTeamIdScopesToThatTeamOnly() {
        Proposal proposal = draftProposal();
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        when(proposalRepository.findVisibleToUserInTeam(1L, 10L)).thenReturn(List.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());

        List<ProposalResponse> responses = proposalService.listProposals(AUTH_HEADER, 10L);

        assertThat(responses).extracting(ProposalResponse::id).containsExactly(100L);
        // 다른 팀(예: 20L)에서 내가 쓴 제안이 새지 않도록, teamId가 지정되면 findVisibleToUser(전체 보기용)가 아니라
        // findVisibleToUserInTeam만 호출돼야 한다.
        verify(proposalRepository, never()).findVisibleToUser(any(), any());
    }

    @Test
    void listProposalsWithTeamIdRejectsNonMember() {
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> proposalService.listProposals(AUTH_HEADER, 10L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("TEAM_ACCESS_DENIED");
    }

    @Test
    void publishProposalTransitionsDraftToOpen() {
        Proposal proposal = draftProposal();
        User teammate = User.builder().id(2L).name("Teammate").build();
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                TeamMember.builder().id(new TeamMemberId(10L, 1L)).team(team).user(author).role("PM").build(),
                TeamMember.builder().id(new TeamMemberId(10L, 2L)).team(team).user(teammate).role("MEMBER").build()));

        ProposalResponse response = proposalService.publishProposal(AUTH_HEADER, 100L);

        assertThat(response.status()).isEqualTo(ProposalStatus.OPEN);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.OPEN);

        // 작성자 본인에게는 알림을 보내지 않고, 나머지 팀원에게만 PROPOSAL_CREATED 알림을 보낸다.
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getUser()).isEqualTo(teammate);
        assertThat(notification.getType()).isEqualTo(NotificationType.PROPOSAL_CREATED);
        assertThat(notification.getProposal()).isEqualTo(proposal);
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

    @Test
    void completeProposalTransitionsConsensusCompletedToCompletedWithDecision() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.CONSENSUS_COMPLETED);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(proposalTargetCultureRepository.findByProposal_Id(100L)).thenReturn(List.of());

        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");
        ProposalResponse response = proposalService.completeProposal(AUTH_HEADER, 100L, request);

        assertThat(response.status()).isEqualTo(ProposalStatus.COMPLETED);
        assertThat(proposal.getDecision()).isEqualTo("B안을 채택합니다.");
        assertThat(proposal.getDecidedBy()).isEqualTo(author);
        assertThat(proposal.getCompletedAt()).isNotNull();
    }

    @Test
    void completeProposalRejectsNonConsensusCompletedStatus() {
        Proposal proposal = draftProposal();
        proposal.setStatus(ProposalStatus.OPEN);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));

        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");

        assertThatThrownBy(() -> proposalService.completeProposal(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_CONSENSUS_COMPLETED");
    }

    @Test
    void completeProposalRejectsNonAuthorWithForbidden() {
        Proposal proposal = draftProposal();
        proposal.setAuthor(User.builder().id(999L).build());
        proposal.setStatus(ProposalStatus.CONSENSUS_READY);
        when(proposalRepository.findById(100L)).thenReturn(Optional.of(proposal));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");

        assertThatThrownBy(() -> proposalService.completeProposal(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_ACCESS_DENIED");
    }

    @Test
    void completeProposalRejectsMissingBearerToken() {
        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");

        assertThatThrownBy(() -> proposalService.completeProposal(null, 100L, request))
                .isInstanceOf(AuthenticationException.class);
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
