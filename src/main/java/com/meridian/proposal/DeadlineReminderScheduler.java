package com.meridian.proposal;

import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.opinion.OpinionRepository;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 마감이 임박했지만(24시간 이내) 아직 팀원 의견을 계속 받고 있는(OPEN/IN_PROGRESS) 제안에 대해,
 * 아직 의견을 남기지 않은 대상 팀원에게 DEADLINE_APPROACHING 알림을 한 번만 보낸다.
 */
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private static final Duration REMINDER_WINDOW = Duration.ofHours(24);

    private final ProposalRepository proposalRepository;
    private final OpinionRepository opinionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void sendDueReminders() {
        Instant now = Instant.now();
        List<Proposal> dueProposals = proposalRepository.findDueForDeadlineReminder(now, now.plus(REMINDER_WINDOW));

        for (Proposal proposal : dueProposals) {
            Set<Long> respondedUserIds = opinionRepository.findAllByProposal_Id(proposal.getId()).stream()
                    .map(opinion -> opinion.getUser().getId())
                    .collect(Collectors.toSet());

            List<TeamMember> members = teamMemberRepository.findAllByTeam_Id(proposal.getTargetTeam().getId());
            for (TeamMember member : members) {
                User user = member.getUser();
                if (respondedUserIds.contains(user.getId())) {
                    continue;
                }
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .proposal(proposal)
                        .type(NotificationType.DEADLINE_APPROACHING)
                        .title("마감 임박")
                        .content("'" + proposal.getTitle() + "' 제안의 마감이 얼마 남지 않았습니다. 아직 의견을 남기지 않으셨어요.")
                        .isRead(false)
                        .build());
            }

            proposal.setDeadlineReminderSent(true);
        }
    }
}
