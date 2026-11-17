package com.example.urlshortener.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security {
    @Bean
    SecurityFilterChain filter(HttpSecurity h) throws Exception {
        return h.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().requestMatchers("/actuator/**").hasAuthority("SCOPE_metrics").requestMatchers("/api/**").authenticated().anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(j -> {
                })).build();
    }
}
