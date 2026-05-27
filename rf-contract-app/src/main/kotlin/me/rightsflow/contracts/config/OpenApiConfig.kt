package me.rightsflow.contracts.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {


    @Value("\${RF_CONTRACT_SVC_HOSTNAME_EXTERNAL:localhost:8090}")
    private lateinit var contractHost: String

    @Value("\${RF_CONTRACT_SVC_PROTOCOL_EXTERNAL:http}")
    private lateinit var protocol: String

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("RightsFlow Contract Service API")
                    .description("Микро-сервис для управления контрактами")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Developer")
                            .email("micxem@yandex.ru")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("$protocol://$contractHost/api/contract/v1")
                        .description("Основной адрес микро-сервиса")
                )
            )
            .components(
                Components()
                    // OAuth2 схема аутентификации
                    .addSecuritySchemes(
                        "oauth2",
                        SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .description("OAuth2 authentication")
                            .flows(
                                OAuthFlows()
/*                                    .authorizationCode(
                                        OAuthFlow()
                                            .authorizationUrl("http://$authHostname:9000/auth/oauth2/authorize")
                                            .tokenUrl("http://$authHostname:9000/auth/oauth2/token")
                                            .refreshUrl("http://$authHostname:9000/auth/oauth2/token")
                                            .scopes(
                                                Scopes()
                                                    .addString("create", "Создание записей")
                                                    .addString("update", "Изменение записей")
                                                    .addString("delete", "Удаление записей")
                                                    .addString("read", "Чтение записей")
                                            )
                                    )*/
                                    .clientCredentials(
                                        OAuthFlow()
                                            .tokenUrl("$protocol://$contractHost/auth/oauth2/token")
                                            .refreshUrl("$protocol://$contractHost/auth/oauth2/token")
                                            /*.scopes(
                                                Scopes()
                                                    .addString("read", "Чтение записей")
                                                    .addString("create", "Создание записей")
                                                    .addString("update", "Изменение записей")
                                                    .addString("delete", "Удаление записей")
                                                    .addString("execute", "Выполнение действий")
                                                    .addString("admin", "Доступ администратора")
                                                    .addString("manager", "Доступ менеджера")
                                                    .addString("user", "Доступ пользователя")
                                            )*/
                                    )
                            )
                    )
                    // Bearer Token схема (для случаев когда токен уже получен)
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
            // Глобальные требования безопасности
            .security(
                listOf(
                    SecurityRequirement().addList("oauth2", listOf("read", "create", "update", "delete", "execute", "admin", "user", "manager")),
                    SecurityRequirement().addList("bearerAuth")
                )
            )
    }
}