package br.com.civilconstructorai.config;

import br.com.civilconstructorai.erro.ProblemDetailsEscritor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Camada 1 do controle de acesso (RF21): rotas públicas, CSRF no padrão SPA (cookie {@code XSRF-TOKEN} +
 * cabeçalho {@code X-XSRF-TOKEN} em toda escrita), CSP restritiva, Referrer-Policy no-referrer e troca do id de
 * sessão no login. Sem formulário de login do Spring: a autenticação é feita pelos endpoints de {@code /auth}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SegurancaConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessaoAbsolutaFilter sessaoAbsoluta, ProblemDetailsEscritor problem) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**", "/api/v1/legal/**", "/api/v1/convites/*/publico", "/api/v1/catalogo/**").permitAll()
                        // ponte pyRevit (RF15, fase 2): autenticada por código de pareamento de uso único e token de dispositivo, não por sessão
                        .requestMatchers("/api/v1/revit/parear", "/api/v1/revit/comandos").permitAll()
                        .requestMatchers("/error", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problem.escrever(res, HttpStatus.UNAUTHORIZED, "nao_autenticado", "Entre para continuar."))
                        .accessDeniedHandler((req, res, ex) -> problem.escrever(res, HttpStatus.FORBIDDEN, "acesso_negado", "Você não tem permissão para esta ação.")))
                .addFilterAfter(sessaoAbsoluta, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
