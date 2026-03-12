package com.anonymous.datahub.data_hub.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityUsersProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            SecurityUsersProperties securityUsersProperties,
            PasswordEncoder passwordEncoder
    ) {
        UserDetails admin = User.withUsername(securityUsersProperties.getAdmin().getUsername())
                .password(passwordEncoder.encode(securityUsersProperties.getAdmin().getPassword()))
                .roles("ADMIN")
                .build();

        UserDetails report = User.withUsername(securityUsersProperties.getReport().getUsername())
                .password(passwordEncoder.encode(securityUsersProperties.getReport().getPassword()))
                .roles("REPORT")
                .build();

        return new InMemoryUserDetailsManager(admin, report);
    }
}
