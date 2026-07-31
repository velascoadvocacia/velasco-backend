package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.TratamentoAdvogado
import java.time.OffsetDateTime

data class UsuarioDTO(
    val id: Long,
    val username: String,
    val pessoa: PessoaResumoResponse,
    val perfil: PerfilUsuario,
    val ufOab: String?,
    val numeroOab: String?,
    val tratamento: TratamentoAdvogado?,
    val ativo: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
