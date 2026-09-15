package com.example.lawyer.exception

import jakarta.validation.ConstraintViolationException
import jakarta.validation.ValidationException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

@Provider
class GlobalExceptionMapper : ExceptionMapper<Throwable> {
    private val logger = Logger.getLogger(GlobalExceptionMapper::class.java)

    override fun toResponse(exception: Throwable): Response {
        val status = when (exception) {
            is BusinessException -> Response.Status.BAD_REQUEST
            is ResourceNotFoundException -> Response.Status.NOT_FOUND
            is ConstraintViolationException, is ValidationException -> Response.Status.BAD_REQUEST
            is UnauthorizedException -> Response.Status.UNAUTHORIZED
            is WebApplicationException -> exception.response.statusInfo
            else -> Response.Status.INTERNAL_SERVER_ERROR
        }

        if (status.statusCode >= 500) {
            logger.error("Erro nao tratado", exception)
        } else {
            logger.debug("Erro de aplicacao", exception)
        }

        val message = when (exception) {
            is ConstraintViolationException -> exception.constraintViolations.joinToString("; ") { it.message }
            else -> exception.message ?: status.reasonPhrase
        }

        val response = if (exception is WebApplicationException) {
            Response.fromResponse(exception.response)
        } else {
            Response.status(status)
        }
        return response
            .entity(ErrorResponse(message = message, status = status.statusCode))
            .build()
    }
}
