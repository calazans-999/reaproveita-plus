
package br.com.reaproveita.security;

import br.com.reaproveita.repository.UserRepository;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            var user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() ->
                    new UsernameNotFoundException("Credenciais invalidas")
                );

            return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
        };
    }

    // =========================================================
    // API REST: HTTP Basic, stateless e sem login por formulario
    // =========================================================

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {

        http
            .securityMatcher("/api/**", "/actuator/**")

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .requestCache(cache -> cache.disable())

            .formLogin(form -> form.disable())

            .httpBasic(Customizer.withDefaults())

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(
                    (request, response, exception) -> {
                        response.setHeader(
                            "WWW-Authenticate",
                            "Basic realm=\"Reaproveita\""
                        );

                        response.sendError(
                            HttpStatus.UNAUTHORIZED.value()
                        );
                    }
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Cadastro publico de usuarios
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register"
                ).permitAll()

                // Health checks publicos
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/health",
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()

                // =============================================
                // INTELIGENCIA ARTIFICIAL - NOVO ENDPOINT
                // =============================================

                // Somente DONOR e ADMIN podem analisar alimentos.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/ai/analyze-donation"
                ).hasAnyRole("DONOR", "ADMIN")

                // =============================================
                // REGRAS EXISTENTES
                // =============================================

                // Endpoints administrativos
                .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                // Endpoints exclusivos de doadores
                .requestMatchers("/api/v1/donor/**")
                    .hasAnyRole("DONOR", "ADMIN")

                // Endpoints exclusivos de receptores
                .requestMatchers("/api/v1/receiver/**")
                    .hasAnyRole("RECEIVER", "ADMIN")

                // Demais endpoints da API exigem autenticacao
                .requestMatchers("/api/v1/**")
                    .authenticated()

                // Qualquer outro caminho nao autorizado
                .anyRequest()
                    .denyAll()
            );

        return http.build();
    }

    // =========================================================
    // INTERFACE WEB: login por formulario, sessao e CSRF
    // =========================================================

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {

        http
            .csrf(Customizer.withDefaults())

            .authorizeHttpRequests(auth -> auth

                // Permite tratar erros internos sem redirecionar
                // respostas de erro para a pagina de login.
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()

                // Paginas e recursos publicos
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/error",
                    "/css/**",
                    "/favicon.ico"
                ).permitAll()

                // Demais paginas exigem usuario autenticado
                .anyRequest()
                    .authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
