package com.example.lawyer.resource

import com.example.lawyer.dto.request.AuthLoginRequest
import com.example.lawyer.dto.response.AuthLoginResponse
import com.example.lawyer.service.AuthService
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class AuthResource(private val service: AuthService) {
    @POST
    @Path("/login")
    @PermitAll
    fun login(@Valid request: AuthLoginRequest): AuthLoginResponse = service.login(request)
}
