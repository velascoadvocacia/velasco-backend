package com.example.lawyer.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
open class Endereco(
    @Column(name = "endereco_rua", length = 150)
    open var rua: String? = null,

    @Column(name = "endereco_numero", length = 20)
    open var numero: String? = null,

    @Column(name = "endereco_complemento", length = 100)
    open var complemento: String? = null,

    @Column(name = "endereco_bairro", length = 100)
    open var bairro: String? = null,

    @Column(name = "endereco_cidade", length = 100)
    open var cidade: String? = null,

    @Column(name = "endereco_estado", length = 2)
    open var estado: String? = null,

    @Column(name = "endereco_cep", length = 8)
    open var cep: String? = null
)
