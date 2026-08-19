package com.meridian.notification;

import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;
    private User user;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(userService, notificationRepository);
        user = User.builder().id(1L).build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(user);
    }

    @Test
    void listsNotificationsForCurrentUserOnly() {
        Notification notification = Notification.builder()
                .user(user)
                .type(NotificationType.PROPOSAL_CREATED)
                .title("새 제안")
                .content("새로운 제안이 등록되었습니다.")
                .isRead(false)
                .build();
        when(notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.listNotifications(AUTH_HEADER);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).type()).isEqualTo(NotificationType.PROPOSAL_CREATED);
        assertThat(responses.get(0).isRead()).isFalse();
    }

    @Test
    void rejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        assertThatThrownBy(() -> notificationService.listNotifications(null))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void marksOwnedNotificationAsRead() {
        Notification notification = Notification.builder()
                .user(user)
                .type(NotificationType.OPINION_REQUESTED)
                .title("의견 요청")
                .content("팀원 의견을 남겨주세요.")
                .isRead(false)
                .build();
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.markRead(AUTH_HEADER, 10L);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    void rejectsMarkingSomeoneElsesNotification() {
        Notification notification = Notification.builder()
                .user(User.builder().id(999L).build())
                .type(NotificationType.OPINION_REQUESTED)
                .title("의견 요청")
                .content("팀원 의견을 남겨주세요.")
                .isRead(false)
                .build();
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(AUTH_HEADER, 10L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("NOTIFICATION_ACCESS_DENIED");
    }

    @Test
    void throwsNotFoundForMissingNotification() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(AUTH_HEADER, 404L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("NOTIFICATION_NOT_FOUND");
    }
}
