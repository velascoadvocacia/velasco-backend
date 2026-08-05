package com.example.lawyer.resource

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportImageRequest
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.response.RtPreviewResponse
import com.example.lawyer.service.RtExportService
import com.example.lawyer.service.ProcessoAnexoService
import com.example.lawyer.service.RtTemplateService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.PartType
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.jboss.logging.Logger
import java.nio.file.Files

@Path("/rt")
@Consumes(MediaType.APPLICATION_JSON)
class RtExportResource(
    private val service: RtExportService,
    private val templateService: RtTemplateService,
    private val processoAnexoService: ProcessoAnexoService,
    private val objectMapper: ObjectMapper
) {
    @POST
    @Path("/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun preview(@Valid request: RtPreviewRequest): RtPreviewResponse {
        return RtPreviewResponse(
            request.processoId,
            templateService.generateSelectedBlocks(request)
        )
    }

    @POST
    @Path("/export")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(DOCX_MEDIA_TYPE)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun export(
        @RestForm("payload") @PartType(MediaType.TEXT_PLAIN) payload: String?,
        @RestForm("documento") @PartType(MediaType.TEXT_PLAIN) documentoLegado: String?,
        @RestForm(FileUpload.ALL) arquivos: List<FileUpload>
    ): Response {
        val payloadJson = payload ?: documentoLegado
        if (payloadJson.isNullOrBlank()) {
            throw com.example.lawyer.exception.BusinessException(
                "Campo multipart 'payload' é obrigatório e deve conter o JSON da exportação"
            )
        }
        val request = runCatching { objectMapper.readValue(payloadJson, RtExportRequest::class.java) }
            .getOrElse { error ->
                throw com.example.lawyer.exception.BusinessException("Campo multipart 'payload' contém JSON inválido")
            }
        return exportRequest(request, arquivos)
    }

    private fun exportRequest(request: RtExportRequest, arquivos: List<FileUpload>): Response {
        logger.infof(
            "RT export multipart: arquivos recebidos=%d; campos=%s",
            arquivos.size,
            arquivos.joinToString(prefix = "[", postfix = "]") { it.name() }
        )
        val imagensPorBloco = arquivos
            .mapIndexed { index, file ->
                val campo = if (file.name().matches(Regex("anexo_.+_\\d+"))) {
                    file.name()
                } else {
                    "anexo_baixa_ctps_tutela_$index"
                }
                campo to file
            }
            .groupBy { (campo, _) ->
                canonicalBlockId(campo.removePrefix("anexo_").replace(Regex("_\\d+$"), ""))
            }
            .mapValues { (_, entries) -> entries.map { (_, file) ->
                val contentType = file.contentType().lowercase()
                if (contentType !in ALLOWED_IMAGE_TYPES) {
                    throw com.example.lawyer.exception.BusinessException("Tipo de imagem não permitido: $contentType")
                }
                RtExportImageRequest(
                    bytes = Files.readAllBytes(file.uploadedFile()),
                    contentType = contentType,
                    nomeOriginal = file.fileName()
                )
            } }
        logger.infof(
            "RT export multipart: associação por bloco=%s",
            imagensPorBloco.entries.joinToString(prefix = "[", postfix = "]") { "${it.key}=${it.value.size}" }
        )
        val generatedBlocks = if (request.blocosSelecionados.isNotEmpty()) {
            templateService.generateSelectedBlocks(
                RtPreviewRequest(
                    processoId = request.processoId,
                    reclamantesIds = request.reclamantesIds,
                    reclamadasIds = request.reclamadasIds,
                    advogadosIds = request.advogadosIds,
                    blocosSelecionados = request.blocosSelecionados,
                    dadosVariaveis = request.dadosVariaveis
                )
            ).map { block ->
                val anexos = imagensPorBloco[block.id].orEmpty().ifEmpty {
                    block.anexos.map {
                        RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url)
                    }
                }
                logger.infof("RT export bloco '%s' (%s): anexos associados=%d", block.titulo, block.id, anexos.size)
                RtExportBlockRequest(
                    id = block.id,
                    title = block.titulo,
                    content = block.texto,
                    anexos = anexos
                )
            }
        } else {
            val anexosCtps = request.processoId?.let { processoAnexoService.list(it) }.orEmpty()
            request.blocks.map { block ->
                val blocoId = block.id ?: blockIdFromTitle(block.title)
                val anexosMultipart = blocoId?.let { imagensPorBloco[it] }.orEmpty()
                val anexosPersistidos = if (blocoId == "baixa_ctps_tutela") {
                    anexosCtps.map {
                        RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url)
                    }
                } else emptyList()
                val resolved = if (block.anexos.isEmpty()) {
                    block.copy(anexos = anexosMultipart.ifEmpty { anexosPersistidos })
                } else block
                logger.infof("RT export bloco legado '%s': anexos associados=%d", resolved.title, resolved.anexos.size)
                resolved
            }
        }
        if (generatedBlocks.isEmpty()) {
            throw com.example.lawyer.exception.BusinessException("Informe ao menos um bloco para exportação")
        }
        val buffer = service.generate(request.copy(blocks = generatedBlocks))

        return Response.ok(buffer, DOCX_MEDIA_TYPE)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"RT - ${sanitizeFilename(request.claimantName)}.docx\""
            )
            .build()
    }

    private fun sanitizeFilename(value: String): String =
        value.replace(Regex("[\\r\\n\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Reclamante" }

    private fun blockIdFromTitle(title: String): String? {
        val normalized = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return when {
            "baixa na ctps" in normalized -> "baixa_ctps_tutela"
            "grupo economico" in normalized -> "responsabilidade_solidaria_grupo_economico"
            else -> null
        }
    }

    private fun canonicalBlockId(value: String): String = when (value) {
        "baixa_ctps", "baixa_ctps_fisica" -> "baixa_ctps_tutela"
        else -> value
    }

    private companion object {
        val logger: Logger = Logger.getLogger(RtExportResource::class.java)
        const val DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png")
    }
}
