package cometfit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentSettingsRepository extends JpaRepository<ConsentSettings, Long> {
    default ConsentSettings forStudent(Long studentId) {
        return findById(studentId).orElseGet(() -> save(new ConsentSettings(studentId)));
    }
}
