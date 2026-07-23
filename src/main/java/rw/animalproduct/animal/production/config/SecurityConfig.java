package rw.animalproduct.animal.production.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/email-diagnostic/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                        .requestMatchers("/", "/login").permitAll()
                        .requestMatchers("/api/upload/**").authenticated()
                        .requestMatchers("/api/locations/**").authenticated()
                        .requestMatchers("/register", "/register/new").hasRole("ADMIN")
                        .requestMatchers("/users/**").hasRole("ADMIN")

                        // ── NEW: admin-only settings sub-areas. Must come
                        // BEFORE the broader "/settings/**" rule below, same
                        // ordering convention already used for /users/** vs
                        // the general rules — Spring Security uses the FIRST
                        // matching rule, so specific-before-general matters.
                        .requestMatchers("/settings/module-assignment/**").hasRole("ADMIN")
                        .requestMatchers("/settings/manage-users/**").hasRole("ADMIN")
                        // profile / change-password stay open to any logged-in user
                        .requestMatchers("/settings/**").authenticated()

                        // ── CHANGED: added VETERINARIAN alongside your
                        // existing roles so the new user type can actually
                        // reach these areas. Without this, a Veterinarian
                        // account authenticates fine but gets a 403 on
                        // every page — the dynamic menu can show a link,
                        // but Spring Security still has the final say.
                        .requestMatchers("/representatives/**").hasAnyRole("ADMIN", "REGULAR_USER", "VETERINARIAN")
                        .requestMatchers("/beneficiaries/**").hasAnyRole("ADMIN", "REGULAR_USER", "VETERINARIAN")
                        .requestMatchers("/livestock/**").hasAnyRole("ADMIN", "REGULAR_USER", "VETERINARIAN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/user/**").hasAnyRole("ADMIN", "REGULAR_USER", "VETERINARIAN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(new CustomAuthenticationSuccessHandler())
                        .failureUrl("/?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout=true")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/upload/**",
                                "/email-diagnostic/**",
                                "/livestock/lifecycle/test-newborn",
                                "/livestock/lifecycle/test-ready-to-breed",
                                "/livestock/lifecycle/test-due-soon",
                                "/livestock/lifecycle/test-overdue",
                                "/livestock/lifecycle/test-email",
                                "/livestock/lifecycle/test-breeding"
                        )
                );

        return http.build();
    }
}
