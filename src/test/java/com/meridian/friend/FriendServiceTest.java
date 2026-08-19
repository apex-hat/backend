package com.meridian.friend;

import com.meridian.common.exception.DomainException;
import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private FriendService friendService;
    private User requester;
    private User addressee;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(userService, userRepository, friendRequestRepository, notificationRepository);
        requester = User.builder().id(1L).name("황성민").friendCode("MER-AAAA").build();
        addressee = User.builder().id(2L).name("팀원").friendCode("MER-BBBB").build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(requester);
        lenient().when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsRequestAndCreatesNotificationForAddressee() {
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(addressee));
        when(friendRequestRepository.findByRequester_IdAndAddressee_Id(1L, 2L)).thenReturn(Optional.empty());
        when(friendRequestRepository.findByRequester_IdAndAddressee_Id(2L, 1L)).thenReturn(Optional.empty());

        FriendRequestResponse response = friendService.sendRequest(AUTH_HEADER, "#mer-bbbb");

        assertThat(response.status()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(response.requesterId()).isEqualTo(1L);
        assertThat(response.addresseeId()).isEqualTo(2L);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(addressee);
    }

    @Test
    void rejectsUnknownFriendCode() {
        when(userRepository.findByFriendCode("MER-ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.sendRequest(AUTH_HEADER, "MER-ZZZZ"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("찾을 수 없습니다");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsSelfFriendRequest() {
        when(userRepository.findByFriendCode("MER-AAAA")).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> friendService.sendRequest(AUTH_HEADER, "MER-AAAA"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    void rejectsDuplicateRequest() {
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(addressee));
        when(friendRequestRepository.findByRequester_IdAndAddressee_Id(1L, 2L))
                .thenReturn(Optional.of(FriendRequest.builder().requester(requester).addressee(addressee).status(FriendRequestStatus.PENDING).build()));

        assertThatThrownBy(() -> friendService.sendRequest(AUTH_HEADER, "MER-BBBB"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미");
    }

    @Test
    void listsOnlyPendingIncomingRequests() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(addressee);
        FriendRequest pending = FriendRequest.builder().requester(requester).addressee(addressee).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findAllByAddressee_IdAndStatusOrderByCreatedAtDesc(2L, FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));

        List<FriendRequestResponse> responses = friendService.listIncomingRequests(AUTH_HEADER);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo(FriendRequestStatus.PENDING);
    }

    @Test
    void acceptingRequestSetsStatusAndRespondedAt() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(addressee);
        FriendRequest pending = FriendRequest.builder().id(10L).requester(requester).addressee(addressee).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(pending));

        FriendRequestResponse response = friendService.respond(AUTH_HEADER, 10L, true);

        assertThat(response.status()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(pending.getRespondedAt()).isNotNull();
    }

    @Test
    void onlyAddresseeCanRespond() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(requester);
        FriendRequest pending = FriendRequest.builder().id(10L).requester(requester).addressee(addressee).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> friendService.respond(AUTH_HEADER, 10L, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("본인에게 온 요청");
    }

    @Test
    void cannotRespondToAlreadyResolvedRequest() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(addressee);
        FriendRequest resolved = FriendRequest.builder().id(10L).requester(requester).addressee(addressee).status(FriendRequestStatus.ACCEPTED).build();
        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> friendService.respond(AUTH_HEADER, 10L, false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미 처리된");
    }

    @Test
    void listsFriendsFromTheOtherSideOfAcceptedRequests() {
        FriendRequest accepted = FriendRequest.builder().requester(requester).addressee(addressee).status(FriendRequestStatus.ACCEPTED).build();
        when(friendRequestRepository.findAllAcceptedForUser(1L)).thenReturn(List.of(accepted));

        List<FriendResponse> friends = friendService.listFriends(AUTH_HEADER);

        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).userId()).isEqualTo(2L);
        assertThat(friends.get(0).friendCode()).isEqualTo("MER-BBBB");
    }
}
