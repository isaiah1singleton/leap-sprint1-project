package com.neueda.leap.config;

import com.neueda.leap.ApiRoutes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.neueda.leap.ApiRoutes;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    // The same string the Node auth stub signs tokens with. In a real
    // deployment this comes from a secrets manager and is injected via an
    // environment variable, not hardcoded twice - it's spelled out here so
    // the "the two services just agree on a secret" idea is concrete, not
    // abstract.
    @Value("${SHARED_SECRET}")
    private String sharedSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The auth stub's tokens carry roles as a plain "roles" claim
        // (["MISSION_OPERATOR"]) rather than Spring Security's default
        // "scope"/"scp" claim shape - this converter is what teaches Spring
        // Security to read OUR claim instead.
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiRoutes.AUTH_REGISTER, ApiRoutes.AUTH_SIGN_IN,
                                ApiRoutes.AUTH + ApiRoutes.ADMIN_SIGN_IN).permitAll()
                        .requestMatchers(HttpMethod.POST, ApiRoutes.ORDER_VALIDATE).authenticated()
                        .requestMatchers(HttpMethod.GET, ApiRoutes.HELLO).permitAll()
                        .requestMatchers(ApiRoutes.ADMIN_AUDIT, ApiRoutes.ADMIN_AUDIT + "/**").authenticated()
                        .requestMatchers(ApiRoutes.MARKET, ApiRoutes.MARKET_ALL).authenticated()
                        .requestMatchers(ApiRoutes.TAX, ApiRoutes.TAX_ALL).authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// @Configuration
// public class SecurityConfig {

//     @Bean
//     public PasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }

//     @Bean
//     public SecurityFilterChain securityFilterChain(
//             HttpSecurity http,
//             TokenService tokenService) throws Exception {
//         return http
//                 .csrf(csrf -> csrf.disable())
//                 .cors(Customizer.withDefaults())
//                 .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                 .authorizeHttpRequests(requests -> requests
//                         .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
//                         .requestMatchers(HttpMethod.POST, ApiRoutes.AUTH_REGISTER, ApiRoutes.AUTH_SIGN_IN,
//                                 ApiRoutes.AUTH + ApiRoutes.ADMIN_SIGN_IN).permitAll()
//                         .requestMatchers(HttpMethod.POST, ApiRoutes.ORDER_VALIDATE).authenticated()
//                         .requestMatchers(HttpMethod.GET, ApiRoutes.HELLO).permitAll()
//                         .requestMatchers(ApiRoutes.ADMIN_AUDIT, ApiRoutes.ADMIN_AUDIT + "/**").authenticated()
//                         .requestMatchers(ApiRoutes.MARKET, ApiRoutes.MARKET_ALL).authenticated()
//                         .anyRequest().authenticated())
//                 .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
//                         (request, response, error) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
//                 .addFilterBefore(new BearerTokenAuthenticationFilter(tokenService),
//                         UsernamePasswordAuthenticationFilter.class)
//                 .build();
//     }

//     @Bean
//     public CorsConfigurationSource corsConfigurationSource(
//             @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOrigins) {
//         CorsConfiguration configuration = new CorsConfiguration();
//         configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
//         configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
//         configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
//         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//         source.registerCorsConfiguration("/**", configuration);
//         return source;
//     }
// }
