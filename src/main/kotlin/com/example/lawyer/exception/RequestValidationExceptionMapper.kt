package com.example.lawyer.exception

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException
import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationExceptionMapper
import jakarta.validation.ElementKind
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

@Provider
class RequestValidationExceptionMapper : ExceptionMapper<ResteasyReactiveViolationException> {
    private val delegate = ResteasyReactiveViolationExceptionMapper()
    private val logger = Logger.getLogger(RequestValidationExceptionMapper::class.java)

    override fun toResponse(exception: ResteasyReactiveViolationException): Response {
        // Preserve Quarkus handling of return-value violations and validation headers.
        val response = delegate.toResponse(exception)
        val message = exception.constraintViolations.map { violation ->
            val field = violation.propertyPath.filter { it.kind == ElementKind.PROPERTY }
                .joinToString(".") { it.name }
                .ifEmpty { violation.propertyPath.toString() }
            "$field: ${violation.message}"
        }.sorted().joinToString("; ")
        logger.warnf("Requisicao rejeitada por validacao: %s", message)
        return Response.fromResponse(response)
            .entity(ErrorResponse(message = message, status = response.status))
            .build()
    }
}
