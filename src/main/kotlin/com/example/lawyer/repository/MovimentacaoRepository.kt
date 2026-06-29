package com.example.lawyer.repository

import com.example.lawyer.domain.model.Movimentacao
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MovimentacaoRepository : PanacheRepositoryBase<Movimentacao, Long> {
    fun findActiveById(id: Long): Movimentacao? = find("id = ?1 and ativo = true", id).firstResult()
}
