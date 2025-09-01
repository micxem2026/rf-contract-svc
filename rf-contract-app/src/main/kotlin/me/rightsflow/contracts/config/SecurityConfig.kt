package me.rightsflow.contracts.me.rightsflow.contracts.config

import me.rightsflow.common.config.CustomAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint
) {

    // Цепочка для публичных (actuator, swagger) эндпоинтов
    @Bean
    @Order(1) // Эта цепочка должна быть обработана первой
    fun publicFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/actuator/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/api-docs/**",
                "/api-docs",
                "/v3/api-docs/**",
                "/v3/api-docs",
                "/swagger-resources/**",
                "/webjars/**"
            )
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll() // Разрешить все запросы, которые соответствуют securityMatcher
            }
            .csrf { it.disable() } // Отключаем CSRF для публичных API
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) } // Stateless сессии
            .httpBasic { it.disable() } // Отключаем Basic Auth
            .formLogin { it.disable() } // Отключаем Form Login
        return http.build()
    }

    // Основная цепочка для защищенных API с JWT
    @Bean
    @Order(2) // Эта цепочка будет обработана, если запросы не попали в publicFilterChain
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .httpBasic { it.disable() } // Отключаем Basic Auth
            .formLogin { it.disable() } // Отключаем Form Login
            .exceptionHandling { exceptionConfig ->
                exceptionConfig
                    .authenticationEntryPoint(customAuthenticationEntryPoint) // <-- Настройка для 401
            }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().authenticated() // Все остальные запросы требуют аутентификации JWT
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Разрешенные источники (origins)
        configuration.setAllowedOriginPatterns(
            mutableListOf<String?>(
                "*"
            )
        )

        // Разрешенные методы
        configuration.setAllowedMethods(
            mutableListOf<String?>(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
            )
        )

        // Разрешенные заголовки
        configuration.allowedHeaders = mutableListOf<String?>(
            "*"
        )

        // Разрешить отправку cookies и авторизационных заголовков
        configuration.allowCredentials = true

        // Заголовки, которые клиент может читать
        configuration.exposedHeaders = mutableListOf<String?>(
            "Authorization", "Cache-Control", "Content-Type"
        )

        // Время кэширования preflight запроса
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun roleHierarchy(): RoleHierarchy {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > SCOPE_admin > ROLE_MANAGER > SCOPE_manager > ROLE_USER > SCOPE_user > SCOPE_read")
    }

    @Bean
    fun methodSecurityExpressionHandler(roleHierarchy: RoleHierarchy?): MethodSecurityExpressionHandler {
        val expressionHandler = DefaultMethodSecurityExpressionHandler()
        expressionHandler.setRoleHierarchy(roleHierarchy)
        return expressionHandler
    }

}