package com.example.lawyer.domain.model

import com.example.lawyer.domain.enums.TipoResponsabilidade
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

@Embeddable
open class EstrategiaProcessual(
    @Column(name = "proc_fundamentos_faticos", length = 5000)
    open var fundamentosFaticos: String? = null,

    @Column(name = "proc_pedidos_principais", length = 5000)
    open var pedidosPrincipais: String? = null,

    @Column(name = "proc_observacoes_internas", length = 5000)
    open var observacoesInternas: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "proc_responsabilidade_pretendida", length = 30)
    open var responsabilidadePretendida: TipoResponsabilidade? = null,

    @Column(name = "proc_possui_grupo_economico", nullable = false)
    open var possuiGrupoEconomico: Boolean = false,

    @Column(name = "proc_possui_acidente_trabalho", nullable = false)
    open var possuiAcidenteTrabalho: Boolean = false,

    @Column(name = "proc_possui_doenca_ocupacional", nullable = false)
    open var possuiDoencaOcupacional: Boolean = false,

    @Column(name = "proc_requer_emissao_cat", nullable = false)
    open var requerEmissaoCat: Boolean = false,

    @Column(name = "proc_valor_causa", precision = 12, scale = 2)
    open var valorCausa: BigDecimal? = null
)
