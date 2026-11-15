package cometfit.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the API gateway interceptor and allows the browser build of the mobile client (§5.3.2 Client <-> API Gateway). */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiGatewayInterceptor gateway;
    private final String[] origins;

    public WebConfig(ApiGatewayInterceptor gateway, @Value("${cometfit.cors-origins}") String origins) {
        this.gateway = gateway;
        this.origins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gateway).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
