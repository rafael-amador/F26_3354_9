package cometfit.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppModuleRepository extends JpaRepository<AppModule, String> {
    List<AppModule> findAllByOrderByModuleId();
}
