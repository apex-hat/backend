package com.meridian.opinion;

import com.meridian.activity.ActivityLogService;
import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
import com.meridian.notification.NotificationRepository;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.proposal.ProposalStatus;
import com.meridian.realtime.TeamEventPublisher;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpinionServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private ProposalService proposalService;
    @Mock
    private OpinionRepository opinionRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamEventPublisher teamEventPublisher;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ActivityLogService activityLogService;

    private OpinionService opinionService;

    private User author;
    private User member;
    private Team team;
    private Proposal proposal;

    @BeforeEach
    void setUp() {
        opinionService = new OpinionService(userService, proposalService, opinionRepository, teamMemberRepository,
                teamEventPublisher, notificationRepository, activityLogService);

        author = User.builder().id(1L).firebaseUid("author-uid").name("Author").build();
        member = User.builder().id(2L).firebaseUid("member-uid").name("Member").build();
        team = Team.builder().id(10L).name("Design Team").build();
        proposal = Proposal.builder().id(100L).title("Title").content("Content")
                .author(author).targetTeam(team).status(ProposalStatus.OPEN).build();

        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(member);
    }

    @Test
    void createsOpinionAndTransitionsOpenToInProgress() {
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);
        when(opinionRepository.existsByProposal_IdAndUser_Id(100L, 2L)).thenReturn(false);
        when(opinionRepository.save(any(Opinion.class))).thenAnswer(invocation -> {
            Opinion opinion = invocation.getArgument(0);
            opinion.setId(1000L);
            return opinion;
        });
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(twoMemberTeam());
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(
                Opinion.builder().id(1000L).proposal(proposal).user(member).stance(OpinionStance.AGREE).build()));

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "동의합니다.", null);
        OpinionResponse response = opinionService.createOpinion(AUTH_HEADER, 100L, request);

        assertThat(response.id()).isEqualTo(1000L);
        assertThat(response.content()).isEqualTo("동의합니다.");
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.IN_PROGRESS);
    }

    @Test
    void createsOpinionWithoutExtraTransitionWhenAlreadyInProgress() {
        proposal.setStatus(ProposalStatus.IN_PROGRESS);
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);
        when(opinionRepository.existsByProposal_IdAndUser_Id(100L, 2L)).thenReturn(false);
        when(opinionRepository.save(any(Opinion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(twoMemberTeam());
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(
                Opinion.builder().id(1L).proposal(proposal).user(member).stance(OpinionStance.DISAGREE).build()));

        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "반대합니다.", null);
        opinionService.createOpinion(AUTH_HEADER, 100L, request);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.IN_PROGRESS);
    }

    @Test
    void transitionsToConsensusReadyWhenAllTargetMembersHaveResponded() {
        proposal.setStatus(ProposalStatus.IN_PROGRESS);
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);
        when(opinionRepository.existsByProposal_IdAndUser_Id(100L, 2L)).thenReturn(false);
        when(opinionRepository.save(any(Opinion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(twoMemberTeam());
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(
                Opinion.builder().id(1L).proposal(proposal).user(author).stance(OpinionStance.AGREE).build(),
                Opinion.builder().id(2L).proposal(proposal).user(member).stance(OpinionStance.DISAGREE).build()));

        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "반대합니다.", null);
        opinionService.createOpinion(AUTH_HEADER, 100L, request);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CONSENSUS_READY);
    }

    private List<TeamMember> twoMemberTeam() {
        return List.of(
                TeamMember.builder().team(team).user(author).role("PM").build(),
                TeamMember.builder().team(team).user(member).role("MEMBER").build());
    }

    @Test
    void rejectsCreateWhenProposalIsDraft() {
        proposal.setStatus(ProposalStatus.DRAFT);
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_ACCEPTING_OPINIONS");
    }

    @Test
    void rejectsCreateWhenProposalIsConsensusReady() {
        proposal.setStatus(ProposalStatus.CONSENSUS_READY);
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_ACCEPTING_OPINIONS");
    }

    @Test
    void rejectsCreateWhenProposalIsCompleted() {
        proposal.setStatus(ProposalStatus.COMPLETED);
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_ACCEPTING_OPINIONS");
    }

    @Test
    void rejectsDuplicateOpinion() {
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);
        when(opinionRepository.existsByProposal_IdAndUser_Id(100L, 2L)).thenReturn(true);

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(AUTH_HEADER, 100L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("OPINION_ALREADY_EXISTS");
    }

    @Test
    void createPropagatesProposalVisibilityFailure() {
        when(proposalService.getVisibleProposal(999L, member))
                .thenThrow(DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found."));

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(AUTH_HEADER, 999L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    @Test
    void createRejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.createOpinion(null, 100L, request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void listOpinionsReturnsMappedResponses() {
        when(proposalService.getVisibleProposal(100L, member)).thenReturn(proposal);
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(member)
                .stance(OpinionStance.AGREE).comment("좋습니다").build();
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(opinion));

        List<OpinionResponse> responses = opinionService.listOpinions(AUTH_HEADER, 100L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo("좋습니다");
    }

    @Test
    void updatesOwnOpinion() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(member)
                .stance(OpinionStance.AGREE).comment("원래 내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));

        OpinionRequest request = new OpinionRequest(OpinionStance.CONDITIONAL_AGREE, "수정된 내용", "http://a.com/file");
        OpinionResponse response = opinionService.updateOpinion(AUTH_HEADER, 1L, request);

        assertThat(response.stance()).isEqualTo(OpinionStance.CONDITIONAL_AGREE);
        assertThat(response.content()).isEqualTo("수정된 내용");
        assertThat(response.attachmentUrl()).isEqualTo("http://a.com/file");
    }

    @Test
    void rejectsUpdateByNonOwner() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(author)
                .stance(OpinionStance.AGREE).comment("원래 내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));

        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "수정 시도", null);

        assertThatThrownBy(() -> opinionService.updateOpinion(AUTH_HEADER, 1L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("OPINION_ACCESS_DENIED");
    }

    @Test
    void rejectsUpdateWhenOpinionMissing() {
        when(opinionRepository.findById(404L)).thenReturn(Optional.empty());

        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "내용", null);

        assertThatThrownBy(() -> opinionService.updateOpinion(AUTH_HEADER, 404L, request))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("OPINION_NOT_FOUND");
    }

    @Test
    void deletesOwnOpinion() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(member)
                .stance(OpinionStance.AGREE).comment("내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));

        opinionService.deleteOpinion(AUTH_HEADER, 1L);
    }

    @Test
    void rejectsDeleteByNonOwner() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(author)
                .stance(OpinionStance.AGREE).comment("내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));

        assertThatThrownBy(() -> opinionService.deleteOpinion(AUTH_HEADER, 1L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("OPINION_ACCESS_DENIED");
    }

    @Test
    void allowsUpdateByTeamPmAndNotifiesOwner() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(author)
                .stance(OpinionStance.AGREE).comment("원래 내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 2L, "PM")).thenReturn(true);

        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "PM이 수정", null);
        OpinionResponse response = opinionService.updateOpinion(AUTH_HEADER, 1L, request);

        assertThat(response.content()).isEqualTo("PM이 수정");
        verify(notificationRepository).save(any(com.meridian.notification.Notification.class));
        verify(activityLogService).record(eq(team), eq(member), eq(author),
                eq(com.meridian.activity.ActivityAction.OPINION_UPDATED_BY_PM), any());
    }

    @Test
    void allowsDeleteByTeamPmAndNotifiesOwner() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(author)
                .stance(OpinionStance.AGREE).comment("내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 2L, "PM")).thenReturn(true);

        opinionService.deleteOpinion(AUTH_HEADER, 1L);

        verify(notificationRepository).save(any(com.meridian.notification.Notification.class));
        verify(activityLogService).record(eq(team), eq(member), eq(author),
                eq(com.meridian.activity.ActivityAction.OPINION_DELETED_BY_PM), any());
    }

    @Test
    void ownerUpdatingOwnOpinionDoesNotNotifyOrLog() {
        Opinion opinion = Opinion.builder().id(1L).proposal(proposal).user(member)
                .stance(OpinionStance.AGREE).comment("원래 내용").build();
        when(opinionRepository.findById(1L)).thenReturn(Optional.of(opinion));

        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "직접 수정", null);
        opinionService.updateOpinion(AUTH_HEADER, 1L, request);

        verifyNoInteractions(notificationRepository, activityLogService);
    }

    @Test
    void rejectsDeleteWhenOpinionMissing() {
        when(opinionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> opinionService.deleteOpinion(AUTH_HEADER, 404L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("OPINION_NOT_FOUND");
    }
}
