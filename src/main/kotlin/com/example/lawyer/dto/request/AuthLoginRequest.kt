package com.example.lawyer.dto.request

import jakarta.validation.constraints.NotBlank

data class AuthLoginRequest(
    @field:NotBlank
    val username: String?,

    @field:NotBlank
    val senha: String?
)
