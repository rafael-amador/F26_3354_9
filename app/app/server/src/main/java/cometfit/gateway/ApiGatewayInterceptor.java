package cometfit.gateway;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import cometfit.domain.AppModule;
import cometfit.services.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API gateway routing guard — Deliverable 1 §5.3.2 "API Gateway using REST APIs" + "routing to proper call".
 * Before a request is routed to the Nutrition / Activity / Social service it asks FeatureFlagService
 * isEnabled(moduleId). A disabled module answers 503 MODULE_DISABLED with a maintenance notice while all other
 * modules keep working (FR-5; SD-4 "show maintenance notice, other features stay available"; FR-5 assumption
 * "Glitches and maintenance are happening in independent components").
 * Module-to-path mapping: A-11.
 */
@Component
public class ApiGatewayInterceptor implements HandlerInterceptor {
    private final FeatureFlagService flags;
    private final ObjectMapper json;

    public ApiGatewayInterceptor(FeatureFlagService flags, ObjectMapper json) {
        this.flags = flags;
        this.json = json;
    }

    public static String moduleFor(String path) {
        if (path.startsWith("/api/nutrition")) return AppModule.NUTRITION;
        if (path.startsWith("/api/activity")) return AppModule.ACTIVITY;
        if (path.startsWith("/api/social")) return AppModule.SOCIAL;
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        String module = moduleFor(req.getRequestURI());
        if (module == null || flags.isEnabled(module)) return true;
        res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), Map.of(
                "code", "MODULE_DISABLED",
                "moduleId", module,
                "message", "This part of CometFit is under maintenance. Other features are still available."));
        return false;
    }
}
