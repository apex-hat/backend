package com.meridian.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m "
            + "where (m.sender.id = :userId1 and m.receiver.id = :userId2) "
            + "or (m.sender.id = :userId2 and m.receiver.id = :userId1) "
            + "order by m.createdAt asc")
    List<Message> findConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
