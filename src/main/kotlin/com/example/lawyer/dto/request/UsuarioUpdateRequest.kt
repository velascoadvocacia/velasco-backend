package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.PerfilUsuario
import jakarta.validation.constraints.Size

data class UsuarioUpdateRequest(
    @field:Size(max = 80)
    val username: String?,

    @field:Size(max = 72)
    val senha: String? = null,

    val pessoaId: Long?,

    val perfil: PerfilUsuario?,

    val ativo: Boolean? = true
)
