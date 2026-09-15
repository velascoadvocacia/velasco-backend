package com.example.lawyer.exception

import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalExceptionMapperTest {
    private val mapper = GlobalExceptionMapper()

    @Test
    fun `should preserve HTTP 404 in response and error body`() {
        mapper.toResponse(NotFoundException()).use { response ->
            assertEquals(404, response.status)
            assertEquals(404, (response.entity as ErrorResponse).status)
        }
    }

    @Test
    fun `should preserve HTTP status and protocol headers`() {
        val exception = WebApplicationException(Response.status(429).header("Retry-After", "60").build())
        mapper.toResponse(exception).use { response ->
            assertEquals(429, response.status)
            assertEquals(429, (response.entity as ErrorResponse).status)
            assertEquals("60", response.getHeaderString("Retry-After"))
        }
    }

    @Test
    fun `should keep unexpected failures as HTTP 500`() {
        mapper.toResponse(IllegalStateException("Falha inesperada")).use { response ->
            assertEquals(500, response.status)
            assertEquals(500, (response.entity as ErrorResponse).status)
        }
    }
}
