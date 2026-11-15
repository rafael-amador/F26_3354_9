package cometfit.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    List<FriendRequest> findByReceiverIdAndStatus(Long receiverId, String status);

    List<FriendRequest> findBySenderIdAndStatus(Long senderId, String status);

    boolean existsBySenderIdAndReceiverIdAndStatus(Long senderId, Long receiverId, String status);
}
