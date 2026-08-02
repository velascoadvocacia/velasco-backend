package com.example.lawyer.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "processo_anexos")
open class ProcessoAnexo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(name = "processo_id", nullable = false)
    open var processoId: Long = 0,

    @Column(name = "bloco_id", nullable = false, length = 100)
    open var blocoId: String = "",

    @Column(name = "s3_key", nullable = false, unique = true, length = 500)
    open var s3Key: String = "",

    @Column(name = "nome_original", nullable = false, length = 255)
    open var nomeOriginal: String = "",

    @Column(name = "content_type", nullable = false, length = 100)
    open var contentType: String = "",

    @Column(name = "tamanho_bytes", nullable = false)
    open var tamanhoBytes: Long = 0,

    @Column(name = "data_upload", nullable = false)
    open var dataUpload: OffsetDateTime = OffsetDateTime.now()
) : AuditableEntity()
