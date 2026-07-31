package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.TratamentoAdvogado
import jakarta.validation.constraints.Size

data class UsuarioCreateRequest(
    @field:Size(max = 80)
    val username: String?,

    @field:Size(max = 72)
    val senha: String?,

    val pessoaId: Long?,

    val perfil: PerfilUsuario?,

    val ufOab: String? = null,

    val numeroOab: String? = null,

    val tratamento: TratamentoAdvogado? = null,

    val ativo: Boolean? = true
)
