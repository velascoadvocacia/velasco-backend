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
        @RestForm("arquivos") arquivos: List<FileUpload>
    ): Response {
        if (payload.isNullOrBlank()) {
            throw com.example.lawyer.exception.BusinessException(
                "Campo multipart 'payload' é obrigatório e deve conter o JSON da exportação"
            )
        }
        val request = runCatching { objectMapper.readValue(payload, RtExportRequest::class.java) }
            .getOrElse { error ->
                throw com.example.lawyer.exception.BusinessException("Campo multipart 'payload' contém JSON inválido")
            }
        return exportRequest(request, arquivos)
    }

    private fun exportRequest(request: RtExportRequest, arquivos: List<FileUpload>): Response {
        val imagensPorBloco = arquivos
            .filter { it.name().matches(Regex("anexo_.+_\\d+")) }
            .groupBy { it.name().removePrefix("anexo_").replace(Regex("_\\d+$"), "") }
            .mapValues { (_, files) -> files.map { file ->
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
                RtExportBlockRequest(
                    title = block.titulo,
                    content = block.texto,
                    anexos = imagensPorBloco[block.id].orEmpty().ifEmpty {
                        block.anexos.map { RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url) }
                    }
                )
            }
        } else {
            val anexosCtps = request.processoId?.let(processoAnexoService::list).orEmpty()
            request.blocks.map { block ->
                if (block.title == "3. Baixa na CTPS física. Tutela antecipada" && block.anexos.isEmpty()) {
                    block.copy(anexos = imagensPorBloco["baixa_ctps_tutela"].orEmpty().ifEmpty {
                        anexosCtps.map { RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url) }
                    })
                } else block
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

    private companion object {
        const val DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png")
    }
}
