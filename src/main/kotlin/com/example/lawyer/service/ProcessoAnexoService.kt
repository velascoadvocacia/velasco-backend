package com.example.lawyer.service

import com.example.lawyer.domain.model.ProcessoAnexo
import com.example.lawyer.dto.response.ProcessoAnexoResponse
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.repository.ProcessoAnexoRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.multipart.FileUpload
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class ProcessoAnexoService(
    private val repository: ProcessoAnexoRepository,
    private val processoService: ProcessoService,
    @ConfigProperty(name = "aws.s3.bucket") private val bucket: String,
    @ConfigProperty(name = "aws.s3.region") private val region: String,
    @ConfigProperty(name = "aws.s3.presigned-url-expiration-seconds") private val expirationSeconds: Long,
    @ConfigProperty(name = "aws.s3.max-file-size-bytes") private val maxFileSize: Long
) {
    private val blocoId = "baixa_ctps_tutela"
    private val s3 = S3Client.builder().region(Region.of(region)).build()
    private val presigner = S3Presigner.builder().region(Region.of(region)).build()

    @Transactional
    fun upload(processoId: Long, files: List<FileUpload>): List<ProcessoAnexoResponse> {
        processoService.findEntity(processoId)
        if (files.isEmpty()) throw BusinessException("Informe ao menos uma imagem")
        return files.map { file ->
            val contentType = file.contentType().lowercase()
            if (contentType !in ALLOWED_TYPES) throw BusinessException("Tipo de arquivo não permitido: $contentType")
            if (file.size() > maxFileSize) throw BusinessException("Arquivo excede o tamanho máximo permitido")
            val extension = contentType.substringAfter('/', "bin")
            val key = "processos/$processoId/ctps/${UUID.randomUUID()}.$extension"
            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file.uploadedFile())
            )
            repository.persist(
                ProcessoAnexo(
                    processoId = processoId,
                    blocoId = blocoId,
                    s3Key = key,
                    nomeOriginal = file.fileName().take(255),
                    contentType = contentType,
                    tamanhoBytes = file.size(),
                    dataUpload = OffsetDateTime.now()
                )
            )
            toResponse(repository.find("s3Key", key).firstResult()!!)
        }
    }

    fun list(processoId: Long): List<ProcessoAnexoResponse> {
        processoService.findEntity(processoId)
        return repository.findByProcesso(processoId, blocoId).map(::toResponse)
    }

    @Transactional
    fun delete(processoId: Long, anexoId: Long) {
        processoService.findEntity(processoId)
        val anexo = repository.findByProcessoAndId(processoId, anexoId, blocoId)
            ?: throw ResourceNotFoundException("Anexo $anexoId nao encontrado")
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(anexo.s3Key).build())
        repository.delete(anexo)
    }

    private fun toResponse(anexo: ProcessoAnexo): ProcessoAnexoResponse =
        ProcessoAnexoResponse(
            id = anexo.id!!,
            processoId = anexo.processoId,
            blocoId = anexo.blocoId,
            nomeOriginal = anexo.nomeOriginal,
            contentType = anexo.contentType,
            tamanhoBytes = anexo.tamanhoBytes,
            url = presignedUrl(anexo.s3Key),
            dataUpload = anexo.dataUpload
        )

    private fun presignedUrl(key: String): String {
        val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
        val presign = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .getObjectRequest(request)
            .build()
        return presigner.presignGetObject(presign).url().toString()
    }

    companion object {
        private val ALLOWED_TYPES = setOf("image/jpeg", "image/png")
    }
}
