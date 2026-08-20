package com.meridian.opinion;

import com.meridian.common.exception.DomainException;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.proposal.ProposalStatus;
import com.meridian.realtime.TeamEventPublisher;
import com.meridian.realtime.TeamEventType;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * README §8 Opinion API. 인증(UserService)과 Proposal 접근 권한(ProposalService)은
 * 기존 구현을 그대로 재사용하고, 이 클래스는 의견 자체의 등록/조회/수정/삭제만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class OpinionService {

    private final UserService userService;
    private final ProposalService proposalService;
    private final OpinionRepository opinionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventPublisher teamEventPublisher;

    @Transactional
    public OpinionResponse createOpinion(String authorizationHeader, Long proposalId, OpinionRequest request) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Proposal proposal = proposalService.getVisibleProposal(proposalId, user);

        assertAcceptingOpinions(proposal);

        if (opinionRepository.existsByProposal_IdAndUser_Id(proposalId, user.getId())) {
            throw DomainException.conflict("OPINION_ALREADY_EXISTS", "이미 이 제안에 의견을 등록했습니다.");
        }

        Opinion opinion = opinionRepository.save(Opinion.builder()
                .proposal(proposal)
                .user(user)
                .stance(request.stance())
                .comment(request.content())
                .attachmentUrl(request.attachmentUrl())
                .build());

        // README §14: 팀원이 첫 의견을 등록하면 OPEN -> IN_PROGRESS로 자동 전이한다.
        if (proposal.getStatus() == ProposalStatus.OPEN) {
            proposal.setStatus(ProposalStatus.IN_PROGRESS);
        }

        // README §14: 대상 팀원 전원이 의견을 등록하면 IN_PROGRESS -> CONSENSUS_READY(분석 가능)로 자동 전이한다.
        if (proposal.getStatus() == ProposalStatus.IN_PROGRESS && allTargetMembersResponded(proposal)) {
            proposal.setStatus(ProposalStatus.CONSENSUS_READY);
        }

        teamEventPublisher.publish(TeamEventType.OPINION_CREATED, proposal.getTargetTeam().getId(), proposal.getId());

        return OpinionResponse.from(opinion);
    }

    public List<OpinionResponse> listOpinions(String authorizationHeader, Long proposalId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        proposalService.getVisibleProposal(proposalId, user);

        return opinionRepository.findAllByProposal_Id(proposalId).stream()
                .map(OpinionResponse::from)
                .toList();
    }

    @Transactional
    public OpinionResponse updateOpinion(String authorizationHeader, Long opinionId, OpinionRequest request) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Opinion opinion = findOpinion(opinionId);
        assertOwner(opinion, user);

        opinion.setStance(request.stance());
        opinion.setComment(request.content());
        opinion.setAttachmentUrl(request.attachmentUrl());

        Proposal proposal = opinion.getProposal();
        teamEventPublisher.publish(TeamEventType.OPINION_UPDATED, proposal.getTargetTeam().getId(), proposal.getId());

        return OpinionResponse.from(opinion);
    }

    @Transactional
    public void deleteOpinion(String authorizationHeader, Long opinionId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Opinion opinion = findOpinion(opinionId);
        assertOwner(opinion, user);

        Proposal proposal = opinion.getProposal();
        opinionRepository.delete(opinion);
        teamEventPublisher.publish(TeamEventType.OPINION_DELETED, proposal.getTargetTeam().getId(), proposal.getId());
    }

    private boolean allTargetMembersResponded(Proposal proposal) {
        int totalMembers = teamMemberRepository.findAllByTeam_Id(proposal.getTargetTeam().getId()).size();
        int respondedMembers = opinionRepository.findAllByProposal_Id(proposal.getId()).size();
        return totalMembers > 0 && respondedMembers >= totalMembers;
    }

    private Opinion findOpinion(Long opinionId) {
        return opinionRepository.findById(opinionId)
                .orElseThrow(() -> DomainException.notFound("OPINION_NOT_FOUND", "Opinion not found."));
    }

    private void assertOwner(Opinion opinion, User user) {
        if (!opinion.getUser().getId().equals(user.getId())) {
            throw DomainException.forbidden("OPINION_ACCESS_DENIED", "본인의 의견만 수정/삭제할 수 있습니다.");
        }
    }

    /** README §14: 의견 등록은 OPEN, IN_PROGRESS 상태에서만 허용한다. */
    private void assertAcceptingOpinions(Proposal proposal) {
        if (proposal.getStatus() != ProposalStatus.OPEN && proposal.getStatus() != ProposalStatus.IN_PROGRESS) {
            throw DomainException.conflict("PROPOSAL_NOT_ACCEPTING_OPINIONS",
                    "OPEN 또는 IN_PROGRESS 상태의 제안에만 의견을 등록할 수 있습니다.");
        }
    }
}
