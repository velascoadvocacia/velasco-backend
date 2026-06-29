package com.example.lawyer.mapper

import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.response.UsuarioDTO
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UsuarioMapper(private val pessoaMapper: PessoaMapper) {
    fun toResponse(entity: Usuario): UsuarioDTO = UsuarioDTO(
        id = entity.id!!,
        username = entity.username,
        pessoa = pessoaMapper.toResumoResponse(entity.pessoa!!),
        perfil = entity.perfil,
        ativo = entity.ativo,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
