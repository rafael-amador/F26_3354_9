package cometfit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CometFit server entry point.
 * Architecture: Deliverable 1 §5.3 "Client-server architecture" — this process is the
 * "Server" box: REST API gateway, Auth gateway, Social / Activity / Nutrition /
 * Administrator services and the encrypted database behind them (A-1).
 */
@SpringBootApplication
@EnableScheduling
public class CometFitApplication {
    public static void main(String[] args) {
        SpringApplication.run(CometFitApplication.class, args);
    }
}
