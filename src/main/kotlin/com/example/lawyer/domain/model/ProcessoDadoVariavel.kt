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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "processo_dados_variaveis",
    uniqueConstraints = [UniqueConstraint(columnNames = ["processo_id", "bloco_id", "campo"])]
)
open class ProcessoDadoVariavel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    open var processo: Processo? = null,

    @Column(name = "bloco_id", nullable = false, length = 100)
    open var blocoId: String = "",

    @Column(nullable = false, length = 150)
    open var campo: String = "",

    @Column(columnDefinition = "TEXT")
    open var valor: String? = null
)
