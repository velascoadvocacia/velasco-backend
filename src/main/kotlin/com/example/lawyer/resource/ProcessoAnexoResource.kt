package com.example.lawyer.resource

import com.example.lawyer.dto.response.ProcessoAnexoResponse
import com.example.lawyer.service.ProcessoAnexoService
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload

@Path("/processos/{processoId}/anexos")
@Produces(MediaType.APPLICATION_JSON)
class ProcessoAnexoResource(private val service: ProcessoAnexoService) {
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun upload(
        @PathParam("processoId") processoId: Long,
        @RestForm("arquivos") arquivos: List<FileUpload>,
        @RestForm("blocoId") blocoId: String?,
        @RestForm("grupo") grupo: String?
    ): Response = Response.status(Response.Status.CREATED)
        .entity(
            service.upload(
                processoId,
                arquivos,
                blocoId ?: ProcessoAnexoService.BAIXA_CTPS_TUTELA,
                grupo ?: ProcessoAnexoService.GRUPO_GERAL
            )
        )
        .build()

    @GET
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun list(
        @PathParam("processoId") processoId: Long,
        @QueryParam("blocoId") @DefaultValue(ProcessoAnexoService.BAIXA_CTPS_TUTELA) blocoId: String
    ): List<ProcessoAnexoResponse> = service.list(processoId, blocoId)

    @DELETE
    @Path("/{anexoId}")
    @RolesAllowed("ADMIN", "ADVOGADO")
    fun delete(
        @PathParam("processoId") processoId: Long,
        @PathParam("anexoId") anexoId: Long,
        @QueryParam("blocoId") @DefaultValue(ProcessoAnexoService.BAIXA_CTPS_TUTELA) blocoId: String
    ): Response {
        service.delete(processoId, anexoId, blocoId)
        return Response.noContent().build()
    }
}
