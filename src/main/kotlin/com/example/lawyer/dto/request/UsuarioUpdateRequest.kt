package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.PerfilUsuario
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UsuarioUpdateRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 80)
    val username: String?,

    @field:Size(min = 8, max = 72)
    val senha: String? = null,

    @field:NotNull
    val pessoaId: Long?,

    @field:NotNull
    val perfil: PerfilUsuario?,

    val ativo: Boolean = true
)
