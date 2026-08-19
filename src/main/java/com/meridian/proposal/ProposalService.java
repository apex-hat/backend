package com.meridian.proposal;

import com.meridian.ai.CultureAnalysis;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * README §7 Proposal API. CRUD(생성/목록/상세/수정/삭제) + 게시(publish)를 담당한다.
 * 완료(complete)는 별도 담당 범위.
 */
@Service
@RequiredArgsConstructor
public class ProposalService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProposalRepository proposalRepository;
    private final ProposalTargetCultureRepository proposalTargetCultureRepository;
    private final CultureAnalysisRepository cultureAnalysisRepository;

    @Transactional
    public ProposalResponse createProposal(String authorizationHeader, ProposalCreateRequest request) {
        User author = resolveCurrentUser(authorizationHeader);

        if (!StringUtils.hasText(request.title()) || !StringUtils.hasText(request.content())) {
            throw DomainException.badRequest("INVALID_PROPOSAL", "title과 content는 필수입니다.");
        }
        if (request.teamId() == null) {
            throw DomainException.badRequest("INVALID_PROPOSAL", "teamId는 필수입니다.");
        }

        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> DomainException.notFound("TEAM_NOT_FOUND", "Team not found."));
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(team.getId(), author.getId())) {
            throw DomainException.forbidden("TEAM_ACCESS_DENIED", "해당 팀에 소속된 사용자만 제안을 생성할 수 있습니다.");
        }
        assertNoDuplicateCultures(request.targetCultures());

        Proposal proposal = Proposal.builder()
                .title(request.title())
                .content(request.content())
                .author(author)
                .targetTeam(team)
                .status(ProposalStatus.DRAFT)
                .deadline(request.deadline())
                .build();
        proposal = proposalRepository.save(proposal);

        List<String> targetCultures = saveTargetCultures(proposal, request.targetCultures());
        linkCultureAnalyses(proposal, request.cultureAnalysisIds());

        return ProposalResponse.from(proposal, targetCultures);
    }

    public List<ProposalResponse> listProposals(String authorizationHeader) {
        User user = resolveCurrentUser(authorizationHeader);

        List<Long> teamIds = teamMemberRepository.findAllByUser_Id(user.getId()).stream()
                .map(member -> member.getTeam().getId())
                .toList();

        List<Proposal> proposals = teamIds.isEmpty()
                ? proposalRepository.findByAuthor_Id(user.getId())
                : proposalRepository.findVisibleToUser(user.getId(), teamIds);

        return proposals.stream()
                .map(proposal -> ProposalResponse.from(proposal, targetCulturesOf(proposal)))
                .toList();
    }

    public ProposalResponse getProposal(String authorizationHeader, Long proposalId) {
        User user = resolveCurrentUser(authorizationHeader);
        Proposal proposal = findProposal(proposalId);

        assertVisible(proposal, user);

        return ProposalResponse.from(proposal, targetCulturesOf(proposal));
    }

    @Transactional
    public ProposalResponse updateProposal(String authorizationHeader, Long proposalId, ProposalUpdateRequest request) {
        User user = resolveCurrentUser(authorizationHeader);
        Proposal proposal = findProposal(proposalId);

        assertVisible(proposal, user);
        assertAuthor(proposal, user);
        assertDraft(proposal, "PROPOSAL_NOT_EDITABLE", "DRAFT 상태의 제안만 수정/삭제할 수 있습니다.");
        assertNoDuplicateCultures(request.targetCultures());

        if (!StringUtils.hasText(request.title()) || !StringUtils.hasText(request.content())) {
            throw DomainException.badRequest("INVALID_PROPOSAL", "title과 content는 필수입니다.");
        }

        proposal.setTitle(request.title());
        proposal.setContent(request.content());
        proposal.setDeadline(request.deadline());

        // Hibernate는 같은 flush 안에서 insert를 delete보다 먼저 실행하므로,
        // (proposal_id, culture_name) UNIQUE 제약을 피하려면 delete를 먼저 flush해야 한다.
        proposalTargetCultureRepository.deleteByProposal_Id(proposal.getId());
        proposalTargetCultureRepository.flush();
        List<String> targetCultures = saveTargetCultures(proposal, request.targetCultures());

        return ProposalResponse.from(proposal, targetCultures);
    }

    @Transactional
    public void deleteProposal(String authorizationHeader, Long proposalId) {
        User user = resolveCurrentUser(authorizationHeader);
        Proposal proposal = findProposal(proposalId);

        assertVisible(proposal, user);
        assertAuthor(proposal, user);
        assertDraft(proposal, "PROPOSAL_NOT_EDITABLE", "DRAFT 상태의 제안만 수정/삭제할 수 있습니다.");

        cultureAnalysisRepository.findByProposal_Id(proposal.getId())
                .forEach(analysis -> analysis.setProposal(null));
        proposalTargetCultureRepository.deleteByProposal_Id(proposal.getId());
        proposalRepository.delete(proposal);
    }

    @Transactional
    public ProposalResponse publishProposal(String authorizationHeader, Long proposalId) {
        User user = resolveCurrentUser(authorizationHeader);
        Proposal proposal = findProposal(proposalId);

        assertVisible(proposal, user);
        assertAuthor(proposal, user);
        assertDraft(proposal, "PROPOSAL_NOT_DRAFT", "DRAFT 상태의 제안만 게시할 수 있습니다.");

        proposal.setStatus(ProposalStatus.OPEN);

        return ProposalResponse.from(proposal, targetCulturesOf(proposal));
    }

    @Transactional
    public ProposalResponse completeProposal(String authorizationHeader, Long proposalId, ProposalCompleteRequest request) {
        User user = resolveCurrentUser(authorizationHeader);
        Proposal proposal = findProposal(proposalId);

        assertVisible(proposal, user);
        assertAuthor(proposal, user);
        if (proposal.getStatus() != ProposalStatus.CONSENSUS_READY) {
            throw DomainException.conflict("PROPOSAL_NOT_CONSENSUS_READY", "CONSENSUS_READY 상태의 제안만 완료 처리할 수 있습니다.");
        }

        proposal.setDecision(request.decision());
        proposal.setDecidedBy(user);
        proposal.setCompletedAt(Instant.now());
        proposal.setStatus(ProposalStatus.COMPLETED);

        return ProposalResponse.from(proposal, targetCulturesOf(proposal));
    }

    /**
     * 다른 도메인(Opinion 등)이 Proposal 접근 권한 검증을 재사용하기 위한 공개 메서드.
     * 기존 findProposal + assertVisible을 그대로 감쌀 뿐, 새로운 접근 제어 로직은 추가하지 않는다.
     */
    public Proposal getVisibleProposal(Long proposalId, User user) {
        Proposal proposal = findProposal(proposalId);
        assertVisible(proposal, user);
        return proposal;
    }

    private Proposal findProposal(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .orElseThrow(() -> DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found."));
    }

    /**
     * DRAFT 제안은 팀원에게도 존재 자체를 노출하지 않는다(README §7).
     * 작성자가 아니면서 팀원도 아니거나, 팀원이어도 아직 DRAFT면 404로 응답해 리소스 존재 여부를 숨긴다.
     */
    private void assertVisible(Proposal proposal, User user) {
        boolean isAuthor = proposal.getAuthor().getId().equals(user.getId());
        if (isAuthor) {
            return;
        }
        boolean isTeamMember = teamMemberRepository.existsByTeam_IdAndUser_Id(proposal.getTargetTeam().getId(), user.getId());
        if (!isTeamMember || proposal.getStatus() == ProposalStatus.DRAFT) {
            throw DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found.");
        }
    }

    /** 조회 가능(assertVisible 통과)하지만 작성자가 아닌 경우에만 도달 — 존재는 알려졌으므로 403. */
    private void assertAuthor(Proposal proposal, User user) {
        if (!proposal.getAuthor().getId().equals(user.getId())) {
            throw DomainException.forbidden("PROPOSAL_ACCESS_DENIED", "작성자만 수행할 수 있는 작업입니다.");
        }
    }

    private void assertDraft(Proposal proposal, String code, String message) {
        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw DomainException.conflict(code, message);
        }
    }

    private void assertNoDuplicateCultures(List<String> cultureNames) {
        if (cultureNames == null || cultureNames.isEmpty()) {
            return;
        }
        long distinctCount = cultureNames.stream().distinct().count();
        if (distinctCount != cultureNames.size()) {
            throw DomainException.badRequest("DUPLICATE_TARGET_CULTURE", "targetCultures에 중복된 문화권이 있습니다.");
        }
    }

    private List<String> saveTargetCultures(Proposal proposal, List<String> cultureNames) {
        if (cultureNames == null || cultureNames.isEmpty()) {
            return List.of();
        }
        List<ProposalTargetCulture> saved = cultureNames.stream()
                .map(name -> ProposalTargetCulture.builder()
                        .proposal(proposal)
                        .cultureName(name)
                        .build())
                .map(proposalTargetCultureRepository::save)
                .toList();
        return saved.stream().map(ProposalTargetCulture::getCultureName).toList();
    }

    private void linkCultureAnalyses(Proposal proposal, List<Long> cultureAnalysisIds) {
        if (cultureAnalysisIds == null || cultureAnalysisIds.isEmpty()) {
            return;
        }
        cultureAnalysisRepository.findAllById(cultureAnalysisIds)
                .forEach(analysis -> analysis.setProposal(proposal));
    }

    private List<String> targetCulturesOf(Proposal proposal) {
        return proposalTargetCultureRepository.findByProposal_Id(proposal.getId()).stream()
                .map(ProposalTargetCulture::getCultureName)
                .collect(Collectors.toList());
    }

    private User resolveCurrentUser(String authorizationHeader) {
        FirebaseUserClaims claims = firebaseTokenVerifier.verify(extractBearerToken(authorizationHeader));
        if (!StringUtils.hasText(claims.uid())) {
            throw new AuthenticationException("Invalid Firebase ID token.");
        }
        return userRepository.findByFirebaseUid(claims.uid())
                .orElseGet(() -> userRepository.save(newUser(claims)));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AuthenticationException(HttpHeaders.AUTHORIZATION + " Bearer token is required.");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AuthenticationException(HttpHeaders.AUTHORIZATION + " Bearer token is required.");
        }
        return token;
    }

    private User newUser(FirebaseUserClaims claims) {
        return User.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .name(claims.name())
                .country(claims.country())
                .timeZone(claims.effectiveTimeZone())
                .location(claims.location())
                .cultureTag(claims.cultureTag())
                .build();
    }
}
