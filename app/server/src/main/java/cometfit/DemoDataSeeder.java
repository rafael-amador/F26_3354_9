package cometfit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import cometfit.security.AccountService;
import cometfit.security.Permissions;
import cometfit.security.UserRepository;

/**
 * Demo accounts for the recorded demo (A-14). Disabled with COMETFIT_SEED_DEMO=false.
 * Fixed TOTP secrets so the presenter can generate codes with app/tools/totp.mjs or any authenticator app.
 * Actors: Developer (UC-4), Student / User / Friend (UC-1..UC-3).
 */
@Component
@ConditionalOnProperty(name = "cometfit.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_PASSWORD = "CometFit123!";
    public static final String DEV_SECRET = "COMETFITDEVADMIN";
    public static final String ALICE_SECRET = "COMETFITALICE234";
    public static final String BOB_SECRET = "COMETFITBOBBY567";

    private final AccountService accounts;
    private final UserRepository users;

    public DemoDataSeeder(AccountService accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByUsernameIgnoreCase("dev")) {
            accounts.createDeveloper("dev", DEMO_PASSWORD, "dev@cometfit.local", "Dev Admin", "OPERATOR",
                    Permissions.DEVELOPER_DEFAULT, DEV_SECRET);
            log.info("Seeded demo developer 'dev' (TOTP secret {})", DEV_SECRET);
        }
        if (!users.existsByUsernameIgnoreCase("alice")) {
            accounts.registerStudent("alice", DEMO_PASSWORD, "alice@cometfit.local", "Alice", ALICE_SECRET);
            log.info("Seeded demo student 'alice' (TOTP secret {})", ALICE_SECRET);
        }
        if (!users.existsByUsernameIgnoreCase("bob")) {
            accounts.registerStudent("bob", DEMO_PASSWORD, "bob@cometfit.local", "Bob", BOB_SECRET);
            log.info("Seeded demo student 'bob' (TOTP secret {})", BOB_SECRET);
        }
    }
}
