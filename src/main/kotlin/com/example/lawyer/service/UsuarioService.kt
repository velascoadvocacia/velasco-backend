package com.example.lawyer.service

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.UsuarioCreateRequest
import com.example.lawyer.dto.request.UsuarioUpdateRequest
import com.example.lawyer.dto.response.UsuarioDTO
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.mapper.UsuarioMapper
import com.example.lawyer.repository.UsuarioRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger

@ApplicationScoped
class UsuarioService(
    private val repository: UsuarioRepository,
    private val pessoaService: PessoaService,
    private val usuarioMapper: UsuarioMapper
) {
    private val logger = Logger.getLogger(UsuarioService::class.java)

    @Transactional
    fun create(request: UsuarioCreateRequest): UsuarioDTO {
        ensureUniqueUsername(request.username!!, null)
        val pessoa = pessoaService.findEntity(request.pessoaId!!)
        val usuario = Usuario(
            username = request.username.trim().lowercase(),
            senha = BcryptUtil.bcryptHash(request.senha!!),
            pessoa = pessoa,
            perfil = request.perfil!!,
            ativo = request.ativo
        )
        repository.persist(usuario)
        logger.infof("Usuario criado id=%s perfil=%s", usuario.id, usuario.perfil)
        return usuarioMapper.toResponse(usuario)
    }

    fun list(username: String?, perfil: PerfilUsuario?, page: Int, size: Int): PageResponse<UsuarioDTO> {
        val params = mutableMapOf<String, Any>()
        val where = mutableListOf("ativo = true")
        username?.takeIf { it.isNotBlank() }?.let {
            where += "lower(username) like :username"
            params["username"] = "%${it.lowercase()}%"
        }
        perfil?.let {
            where += "perfil = :perfil"
            params["perfil"] = it
        }
        val queryText = where.joinToString(" and ").ifBlank { "1 = 1" }
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val query = repository.find(queryText, Sort.by("username").ascending(), params).page(Page.of(safePage, safeSize))
        return PageResponse(query.list().map(usuarioMapper::toResponse), safePage, safeSize, query.count(), query.pageCount())
    }

    fun getById(id: Long): UsuarioDTO = usuarioMapper.toResponse(findEntity(id))

    fun findEntity(id: Long): Usuario =
        repository.findActiveById(id) ?: throw ResourceNotFoundException("Usuario $id nao encontrado")

    fun findActiveByUsername(username: String): Usuario? =
        repository.findByUsername(username.trim().lowercase())?.takeIf { it.ativo }

    @Transactional
    fun update(id: Long, request: UsuarioUpdateRequest): UsuarioDTO {
        val usuario = findEntity(id)
        ensureUniqueUsername(request.username!!, id)
        usuario.username = request.username.trim().lowercase()
        request.senha?.takeIf { it.isNotBlank() }?.let { usuario.senha = BcryptUtil.bcryptHash(it) }
        usuario.pessoa = pessoaService.findEntity(request.pessoaId!!)
        usuario.perfil = request.perfil!!
        usuario.ativo = request.ativo
        logger.infof("Usuario atualizado id=%s", usuario.id)
        return usuarioMapper.toResponse(usuario)
    }

    @Transactional
    fun delete(id: Long) {
        val usuario = findEntity(id)
        usuario.ativo = false
        logger.infof("Usuario inativado id=%s", id)
    }

    private fun ensureUniqueUsername(username: String, id: Long?) {
        if (repository.existsUsernameForAnotherUsuario(username.trim().lowercase(), id)) {
            throw BusinessException("Username ja cadastrado")
        }
    }
}
