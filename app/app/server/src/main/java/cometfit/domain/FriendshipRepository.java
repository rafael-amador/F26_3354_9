package cometfit.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUserAIdOrUserBId(Long a, Long b);

    Optional<Friendship> findByUserAIdAndUserBId(Long a, Long b);

    default Optional<Friendship> between(Long u1, Long u2) {
        return findByUserAIdAndUserBId(Math.min(u1, u2), Math.max(u1, u2));
    }

    default List<Long> friendIdsOf(Long userId) {
        return findByUserAIdOrUserBId(userId, userId).stream().map(f -> f.otherThan(userId)).toList();
    }
}
