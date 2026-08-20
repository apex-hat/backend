package com.meridian.friend;

import com.meridian.common.exception.DomainException;
import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public FriendRequestResponse sendRequest(String authorizationHeader, String friendCode) {
        User requester = userService.getCurrentUserEntity(authorizationHeader);

        if (!StringUtils.hasText(friendCode)) {
            throw DomainException.badRequest("FRIEND_CODE_REQUIRED", "고유 ID를 입력해주세요.");
        }
        String normalized = friendCode.trim().toUpperCase().replaceFirst("^#", "");

        User addressee = userRepository.findByFriendCode(normalized)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "해당 고유 ID의 사용자를 찾을 수 없습니다."));

        if (addressee.getId().equals(requester.getId())) {
            throw DomainException.badRequest("SELF_FRIEND_REQUEST", "자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }

        boolean alreadyExists = findExisting(requester.getId(), addressee.getId()).isPresent()
                || findExisting(addressee.getId(), requester.getId()).isPresent();
        if (alreadyExists) {
            throw DomainException.conflict("FRIEND_REQUEST_EXISTS", "이미 친구이거나 요청이 진행 중입니다.");
        }

        FriendRequest request = friendRequestRepository.save(FriendRequest.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendRequestStatus.PENDING)
                .build());

        notificationRepository.save(Notification.builder()
                .user(addressee)
                .type(NotificationType.FRIEND_REQUEST)
                .title("친구 요청")
                .content(requester.getName() + "님이 친구 요청을 보냈습니다.")
                .isRead(false)
                .build());

        return FriendRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingRequests(String authorizationHeader) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        return friendRequestRepository.findAllByAddressee_IdAndStatusOrderByCreatedAtDesc(user.getId(), FriendRequestStatus.PENDING)
                .stream()
                .map(FriendRequestResponse::from)
                .toList();
    }

    @Transactional
    public FriendRequestResponse respond(String authorizationHeader, Long requestId, boolean accept) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> DomainException.notFound("FRIEND_REQUEST_NOT_FOUND", "요청을 찾을 수 없습니다."));

        if (!request.getAddressee().getId().equals(user.getId())) {
            throw DomainException.forbidden("FRIEND_REQUEST_ACCESS_DENIED", "본인에게 온 요청만 응답할 수 있습니다.");
        }
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw DomainException.conflict("FRIEND_REQUEST_ALREADY_RESOLVED", "이미 처리된 요청입니다.");
        }

        request.setStatus(accept ? FriendRequestStatus.ACCEPTED : FriendRequestStatus.REJECTED);
        request.setRespondedAt(Instant.now());
        return FriendRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(String authorizationHeader) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        return friendRequestRepository.findAllAcceptedForUser(user.getId()).stream()
                .map(request -> FriendResponse.from(request, user.getId()))
                .toList();
    }

    private Optional<FriendRequest> findExisting(Long requesterId, Long addresseeId) {
        return friendRequestRepository.findByRequester_IdAndAddressee_Id(requesterId, addresseeId);
    }
}
