package com.meridian.message;

import com.meridian.common.exception.DomainException;
import com.meridian.friend.FriendRequestRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
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
class MessageServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private MessageRepository messageRepository;

    private MessageService messageService;
    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(userService, userRepository, friendRequestRepository, messageRepository);
        sender = User.builder().id(1L).name("황성민").build();
        receiver = User.builder().id(2L).name("팀원").build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(sender);
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsMessageBetweenFriends() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendRequestRepository.existsAcceptedBetween(1L, 2L)).thenReturn(true);

        MessageResponse response = messageService.sendMessage(AUTH_HEADER, 2L, "안녕하세요");

        assertThat(response.senderId()).isEqualTo(1L);
        assertThat(response.receiverId()).isEqualTo(2L);
        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void rejectsMessageToNonFriend() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendRequestRepository.existsAcceptedBetween(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.sendMessage(AUTH_HEADER, 2L, "안녕하세요"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("친구 사이에만");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> messageService.sendMessage(AUTH_HEADER, 2L, "   "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("메시지 내용");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rejectsMessageToSelf() {
        assertThatThrownBy(() -> messageService.sendMessage(AUTH_HEADER, 1L, "혼잣말"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("받는 사람");
    }

    @Test
    void rejectsUnknownReceiver() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(AUTH_HEADER, 99L, "안녕하세요"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void getsConversationAndMarksReceivedMessagesRead() {
        when(friendRequestRepository.existsAcceptedBetween(1L, 2L)).thenReturn(true);
        Message incoming = Message.builder().sender(receiver).receiver(sender).content("반갑습니다").isRead(false).build();
        Message outgoing = Message.builder().sender(sender).receiver(receiver).content("네 반가워요").isRead(false).build();
        when(messageRepository.findConversation(1L, 2L)).thenReturn(List.of(incoming, outgoing));

        List<MessageResponse> conversation = messageService.getConversation(AUTH_HEADER, 2L);

        assertThat(conversation).hasSize(2);
        assertThat(incoming.getIsRead()).isTrue();
        assertThat(outgoing.getIsRead()).isFalse();
    }

    @Test
    void rejectsConversationWithNonFriend() {
        when(friendRequestRepository.existsAcceptedBetween(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.getConversation(AUTH_HEADER, 2L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("친구 사이에만");
    }
}
