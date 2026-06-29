package com.example.lawyer.domain.model

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TipoPessoa
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "pessoas")
open class Pessoa(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(nullable = false, length = 150)
    open var nome: String = "",

    @Column(length = 11, unique = true)
    open var cpf: String? = null,

    @Column(length = 14, unique = true)
    open var cnpj: String? = null,

    @Column(nullable = false, length = 180)
    open var email: String = "",

    @Column(length = 20)
    open var telefone: String? = null,

    @Column(length = 60)
    open var nacionalidade: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    open var estadoCivil: EstadoCivil? = null,

    @Column(length = 20)
    open var rg: String? = null,

    @Column(length = 20)
    open var orgaoEmissorRg: String? = null,

    @Column(length = 20)
    open var pis: String? = null,

    @Column(length = 150)
    open var nomeMae: String? = null,

    @Column(length = 120)
    open var profissao: String? = null,

    @Column(length = 180)
    open var razaoSocial: String? = null,

    @Column(length = 180)
    open var nomeFantasia: String? = null,

    @Column(length = 30)
    open var inscricaoEstadual: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var tipoPessoa: TipoPessoa = TipoPessoa.FISICA,

    open var dataNascimento: LocalDate? = null,

    @Embedded
    open var endereco: Endereco? = null,

    @Column(length = 2000)
    open var observacoes: String? = null,

    @Column(nullable = false)
    open var ativo: Boolean = true
) : AuditableEntity()
