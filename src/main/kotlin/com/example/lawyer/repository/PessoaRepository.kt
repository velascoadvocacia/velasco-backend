package com.example.lawyer.repository

import com.example.lawyer.domain.model.Pessoa
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PessoaRepository : PanacheRepositoryBase<Pessoa, Long> {
    fun findByCpf(cpf: String): Pessoa? = find("cpf", cpf).firstResult()

    fun findByCnpj(cnpj: String): Pessoa? = find("cnpj", cnpj).firstResult()

    fun findActiveById(id: Long): Pessoa? = find("id = ?1 and ativo = true", id).firstResult()

    fun existsCpfForAnotherPessoa(cpf: String, id: Long? = null): Boolean {
        val query = if (id == null) "cpf = ?1 and ativo = true" else "cpf = ?1 and id <> ?2 and ativo = true"
        val params = if (id == null) arrayOf(cpf) else arrayOf(cpf, id)
        return count(query, *params) > 0
    }

    fun existsCnpjForAnotherPessoa(cnpj: String, id: Long? = null): Boolean {
        val query = if (id == null) "cnpj = ?1 and ativo = true" else "cnpj = ?1 and id <> ?2 and ativo = true"
        val params = if (id == null) arrayOf(cnpj) else arrayOf(cnpj, id)
        return count(query, *params) > 0
    }

    fun existsEmailForAnotherPessoa(email: String, id: Long? = null): Boolean {
        val query = if (id == null) "lower(email) = ?1 and ativo = true" else "lower(email) = ?1 and id <> ?2 and ativo = true"
        val params = if (id == null) arrayOf(email.lowercase()) else arrayOf(email.lowercase(), id)
        return count(query, *params) > 0
    }
}
