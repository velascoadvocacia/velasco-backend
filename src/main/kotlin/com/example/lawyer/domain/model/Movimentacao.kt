package com.example.lawyer.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "movimentacoes")
open class Movimentacao(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    open var processo: Processo? = null,

    @Column(nullable = false, length = 2000)
    open var descricao: String = "",

    @Column(nullable = false)
    open var dataMovimentacao: OffsetDateTime = OffsetDateTime.now(),

    @Column(nullable = false)
    open var ativo: Boolean = true
) : AuditableEntity()
