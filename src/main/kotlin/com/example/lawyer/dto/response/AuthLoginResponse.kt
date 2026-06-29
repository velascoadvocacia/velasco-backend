package com.example.lawyer.dto.response

data class AuthLoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val usuario: UsuarioDTO
)
