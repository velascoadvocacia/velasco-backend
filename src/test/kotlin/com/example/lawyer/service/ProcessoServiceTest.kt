package com.example.lawyer.service

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.StatusProcesso
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.model.EstrategiaProcessual
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

@QuarkusTest
class ProcessoServiceTest {
    @Inject
    lateinit var service: ProcessoService

    @Inject
    lateinit var em: EntityManager

    @Test
    @TestTransaction
    fun `should filter collection members without duplicating processes or pagination totals`() {
        val pessoas = (1..3).map { Pessoa(nome = "Pessoa $it", email = "filtro$it@example.com").also(em::persist) }
        val advogados = pessoas.take(2).mapIndexed { index, pessoa ->
            Usuario(username = "filtro$index", senha = "test", pessoa = pessoa, perfil = PerfilUsuario.ADVOGADO)
                .also(em::persist)
        }
        fun processo(numero: String, dia: Int, ativo: Boolean = true, status: StatusProcesso = StatusProcesso.ABERTO) =
            Processo(numeroProcesso = numero, descricao = "Teste", dataAbertura = LocalDate.of(2026, 1, dia),
                estrategiaProcessual = EstrategiaProcessual(),
                ativo = ativo, status = status, reclamantes = pessoas.take(2).toMutableSet(),
                advogados = advogados.toMutableSet()).also(em::persist)

        val antigo = processo("FILTRO-1", 1)
        val recente = processo("FILTRO-2", 2)
        processo("FILTRO-INATIVO", 3, ativo = false)
        processo("FILTRO-FINALIZADO", 4, status = StatusProcesso.FINALIZADO)
        processo("OUTRO", 5).apply {
            reclamantes = mutableSetOf(pessoas[2])
            this.advogados = mutableSetOf(advogados[0])
        }
        em.flush()
        em.clear()

        for ((clienteId, advogadoId) in listOf(
            pessoas[1].id to null,
            null to advogados[1].id,
            pessoas[1].id to advogados[1].id
        )) {
            val primeira = service.list("filtro", clienteId, advogadoId, StatusProcesso.ABERTO, 0, 1)
            assertEquals(listOf(recente.id), primeira.items.map { it.id })
            assertEquals(2L, primeira.totalItems)
            assertEquals(2, primeira.totalPages)
            val segunda = service.list("filtro", clienteId, advogadoId, StatusProcesso.ABERTO, 1, 1)
            assertEquals(listOf(antigo.id), segunda.items.map { it.id })
        }
        assertEquals(0L, service.list(null, pessoas[2].id, advogados[1].id, null, 0, 10).totalItems)
        assertEquals(0L, service.list(null, null, Long.MAX_VALUE, null, 0, 10).totalItems)
        assertEquals(0L, service.list(null, Long.MAX_VALUE, null, null, 0, 10).totalItems)
        assertEquals(3L, service.list("filtro", null, null, null, 0, 10).totalItems)
    }
}
