package com.example.lawyer.security

import io.quarkus.security.spi.runtime.AuthenticationFailureEvent
import io.quarkus.security.spi.runtime.AuthorizationFailureEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.jboss.logging.Logger

@ApplicationScoped
class SecurityEventLogger {
    private val logger = Logger.getLogger(SecurityEventLogger::class.java)

    fun onAuthenticationFailure(@Observes event: AuthenticationFailureEvent) {
        val failure = event.authenticationFailure
        logger.warnf(
            failure,
            "Falha de autenticacao. properties=%s, cause=%s",
            event.eventProperties,
            failure.message ?: failure.javaClass.simpleName
        )
    }

    fun onAuthorizationFailure(@Observes event: AuthorizationFailureEvent) {
        val identity = event.securityIdentity
        val failure = event.authorizationFailure
        val username = identity?.principal?.name ?: "anonymous"
        logger.warnf(
            failure,
            "Falha de autorizacao. user=%s, roles=%s, context=%s, properties=%s, cause=%s",
            username,
            identity?.roles ?: emptySet<String>(),
            event.authorizationContext,
            event.eventProperties,
            failure.message ?: failure.javaClass.simpleName
        )
    }
}
