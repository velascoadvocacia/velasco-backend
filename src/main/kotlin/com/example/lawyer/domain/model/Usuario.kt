package com.example.lawyer.domain.model

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.TratamentoAdvogado
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "usuarios")
open class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(nullable = false, unique = true, length = 80)
    open var username: String = "",

    @Column(nullable = false, name = "senha_hash", length = 120)
    open var senha: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    open var pessoa: Pessoa? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var perfil: PerfilUsuario = PerfilUsuario.ASSISTENTE,

    @Column(name = "uf_oab", length = 2)
    open var ufOab: String? = null,

    @Column(name = "numero_oab", length = 30)
    open var numeroOab: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    open var tratamento: TratamentoAdvogado? = null,

    @Column(nullable = false)
    open var ativo: Boolean = true
) : AuditableEntity()
