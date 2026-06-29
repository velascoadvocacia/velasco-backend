package com.example.lawyer.repository

import com.example.lawyer.domain.model.Processo
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessoRepository : PanacheRepositoryBase<Processo, Long> {
    fun findActiveById(id: Long): Processo? = find("id = ?1 and ativo = true", id).firstResult()

    fun existsNumeroForAnotherProcesso(numeroProcesso: String, id: Long? = null): Boolean {
        val query = if (id == null) "numeroProcesso = ?1 and ativo = true" else "numeroProcesso = ?1 and id <> ?2 and ativo = true"
        val params = if (id == null) arrayOf(numeroProcesso) else arrayOf(numeroProcesso, id)
        return count(query, *params) > 0
    }
}
