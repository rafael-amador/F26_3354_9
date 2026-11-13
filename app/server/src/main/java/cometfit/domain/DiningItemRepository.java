package cometfit.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningItemRepository extends JpaRepository<DiningItem, Long> {
    Optional<DiningItem> findByExternalId(String externalId);

    List<DiningItem> findByLocationOrderByName(String location);
}
