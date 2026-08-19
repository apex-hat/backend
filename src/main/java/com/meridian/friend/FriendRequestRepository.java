package com.meridian.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findByRequester_IdAndAddressee_Id(Long requesterId, Long addresseeId);

    List<FriendRequest> findAllByAddressee_IdAndStatusOrderByCreatedAtDesc(Long addresseeId, FriendRequestStatus status);

    @Query("select fr from FriendRequest fr "
            + "where fr.status = com.meridian.friend.FriendRequestStatus.ACCEPTED "
            + "and (fr.requester.id = :userId or fr.addressee.id = :userId) "
            + "order by fr.respondedAt desc")
    List<FriendRequest> findAllAcceptedForUser(@Param("userId") Long userId);
}
