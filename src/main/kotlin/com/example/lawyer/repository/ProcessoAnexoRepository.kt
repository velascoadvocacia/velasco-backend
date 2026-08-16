package com.example.lawyer.repository

import com.example.lawyer.domain.model.ProcessoAnexo
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessoAnexoRepository : PanacheRepositoryBase<ProcessoAnexo, Long> {
    fun findByProcesso(processoId: Long, blocoId: String): List<ProcessoAnexo> =
        find("processoId = ?1 and blocoId = ?2 order by grupo, ordem, dataUpload", processoId, blocoId).list()

    fun countByProcessoBlocoGrupo(processoId: Long, blocoId: String, grupo: String): Long =
        count("processoId = ?1 and blocoId = ?2 and grupo = ?3", processoId, blocoId, grupo)

    fun findByProcessoAndId(processoId: Long, id: Long, blocoId: String): ProcessoAnexo? =
        find("processoId = ?1 and id = ?2 and blocoId = ?3", processoId, id, blocoId).firstResult()
}
