package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.PerfilUsuario
import java.time.OffsetDateTime

data class UsuarioDTO(
    val id: Long,
    val username: String,
    val pessoa: PessoaResumoResponse,
    val perfil: PerfilUsuario,
    val ativo: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
