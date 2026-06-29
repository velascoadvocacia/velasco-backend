package com.example.lawyer.repository

import com.example.lawyer.domain.model.Usuario
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UsuarioRepository : PanacheRepositoryBase<Usuario, Long> {
    fun findByUsername(username: String): Usuario? = find("username", username).firstResult()

    fun findActiveById(id: Long): Usuario? = find("id = ?1 and ativo = true", id).firstResult()

    fun existsUsernameForAnotherUsuario(username: String, id: Long? = null): Boolean {
        val query = if (id == null) "username = ?1 and ativo = true" else "username = ?1 and id <> ?2 and ativo = true"
        val params = if (id == null) arrayOf(username) else arrayOf(username, id)
        return count(query, *params) > 0
    }
}
