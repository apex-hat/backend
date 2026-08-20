package com.meridian.teammessage;

import com.meridian.common.exception.DomainException;
import com.meridian.realtime.TeamEventPublisher;
import com.meridian.realtime.TeamEventType;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMessageServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamMessageRepository teamMessageRepository;
    @Mock
    private TeamEventPublisher teamEventPublisher;

    private TeamMessageService teamMessageService;
    private User sender;
    private Team team;

    @BeforeEach
    void setUp() {
        teamMessageService = new TeamMessageService(userService, teamRepository, teamMemberRepository, teamMessageRepository, teamEventPublisher);
        sender = User.builder().id(1L).name("황성민").build();
        team = Team.builder().id(10L).name("Design Team").build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(sender);
        lenient().when(teamMessageRepository.save(any(TeamMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsMessageWhenSenderIsTeamMember() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        TeamMessageResponse response = teamMessageService.sendMessage(AUTH_HEADER, 10L, "안녕하세요");

        assertThat(response.teamId()).isEqualTo(10L);
        assertThat(response.senderId()).isEqualTo(1L);
        assertThat(response.senderName()).isEqualTo("황성민");
        assertThat(response.content()).isEqualTo("안녕하세요");
        verify(teamEventPublisher).publish(TeamEventType.MESSAGE_CREATED, 10L, null);
    }

    @Test
    void rejectsMessageFromNonTeamMember() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> teamMessageService.sendMessage(AUTH_HEADER, 10L, "안녕하세요"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("소속된 사용자만");
        verify(teamMessageRepository, never()).save(any());
        verify(teamEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> teamMessageService.sendMessage(AUTH_HEADER, 10L, "   "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("메시지 내용");
        verify(teamMessageRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownTeam() {
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMessageService.sendMessage(AUTH_HEADER, 999L, "안녕하세요"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Team not found");
    }

    @Test
    void listsMessagesForTeamMember() {
        when(teamRepository.existsById(10L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        TeamMessage message = TeamMessage.builder().id(5L).team(team).sender(sender).content("반갑습니다").build();
        when(teamMessageRepository.findAllByTeam_IdOrderByCreatedAtAsc(10L)).thenReturn(List.of(message));

        List<TeamMessageResponse> messages = teamMessageService.getMessages(AUTH_HEADER, 10L);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("반갑습니다");
    }

    @Test
    void rejectsListingForNonTeamMember() {
        when(teamRepository.existsById(10L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> teamMessageService.getMessages(AUTH_HEADER, 10L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("소속된 사용자만");
    }
}
