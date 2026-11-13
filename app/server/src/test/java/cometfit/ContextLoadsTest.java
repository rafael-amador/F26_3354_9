package cometfit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cometfit.domain.AppModuleRepository;

/** Boots the whole server on H2: Flyway schema must match every JPA entity of the §5.2 class diagram. */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadsTest {
    @Autowired AppModuleRepository modules;

    @Test
    void schemaMatchesEntitiesAndModulesAreSeeded() {
        assertThat(modules.findAllByOrderByModuleId()).extracting("moduleId")
                .containsExactly("ACTIVITY", "NUTRITION", "SOCIAL");
    }
}
