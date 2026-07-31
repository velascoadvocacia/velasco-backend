package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.PerfilUsuario
import jakarta.validation.constraints.Size

data class UsuarioCreateRequest(
    @field:Size(max = 80)
    val username: String?,

    @field:Size(max = 72)
    val senha: String?,

    val pessoaId: Long?,

    val perfil: PerfilUsuario?,

    val oab: String? = null,

    val ativo: Boolean? = true
)
