package cometfit.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceLinkRepository extends JpaRepository<DeviceLink, Long> {
    List<DeviceLink> findByStudentIdAndStatus(Long studentId, String status);

    Optional<DeviceLink> findByStudentIdAndDevice_DeviceId(Long studentId, String deviceId);

    List<DeviceLink> findByDevice_DeviceIdAndStatus(String deviceId, String status);
}
