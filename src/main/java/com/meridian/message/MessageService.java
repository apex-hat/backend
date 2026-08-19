package com.meridian.message;

import com.meridian.common.exception.DomainException;
import com.meridian.friend.FriendRequestRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public MessageResponse sendMessage(String authorizationHeader, Long receiverId, String content) {
        User sender = userService.getCurrentUserEntity(authorizationHeader);

        if (!StringUtils.hasText(content)) {
            throw DomainException.badRequest("MESSAGE_CONTENT_REQUIRED", "메시지 내용을 입력해주세요.");
        }
        if (receiverId == null || receiverId.equals(sender.getId())) {
            throw DomainException.badRequest("INVALID_RECEIVER", "받는 사람을 확인해주세요.");
        }

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (!friendRequestRepository.existsAcceptedBetween(sender.getId(), receiverId)) {
            throw DomainException.forbidden("NOT_FRIENDS", "친구 사이에만 메시지를 보낼 수 있습니다.");
        }

        Message saved = messageRepository.save(Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content.trim())
                .isRead(false)
                .build());

        return MessageResponse.from(saved);
    }

    /** 대화를 조회하면서, 내가 받은 메시지 중 안 읽은 것은 읽음 처리한다. */
    @Transactional
    public List<MessageResponse> getConversation(String authorizationHeader, Long friendUserId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);

        if (!friendRequestRepository.existsAcceptedBetween(user.getId(), friendUserId)) {
            throw DomainException.forbidden("NOT_FRIENDS", "친구 사이에만 대화를 볼 수 있습니다.");
        }

        List<Message> messages = messageRepository.findConversation(user.getId(), friendUserId);
        messages.stream()
                .filter(message -> message.getReceiver().getId().equals(user.getId()) && !message.getIsRead())
                .forEach(message -> message.setIsRead(true));

        return messages.stream().map(MessageResponse::from).toList();
    }
}
