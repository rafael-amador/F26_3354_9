package cometfit.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartDeviceRepository extends JpaRepository<SmartDevice, Long> {
    Optional<SmartDevice> findByDeviceId(String deviceId);
}
