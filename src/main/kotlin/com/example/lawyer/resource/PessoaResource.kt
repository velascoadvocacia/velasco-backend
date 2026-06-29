package com.example.lawyer.resource

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.response.PessoaResponseDTO
import com.example.lawyer.service.PessoaService
import jakarta.annotation.security.DenyAll
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/pessoas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@DenyAll
class PessoaResource(private val service: PessoaService) {
    @POST
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun create(@Valid request: PessoaRequestDTO): Response =
        Response.status(Response.Status.CREATED).entity(service.create(request)).build()

    @GET
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun list(
        @QueryParam("nome") nome: String?,
        @QueryParam("cpf") cpf: String?,
        @QueryParam("tipoPessoa") tipoPessoa: TipoPessoa?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): PageResponse<PessoaResponseDTO> = service.list(nome, cpf, tipoPessoa, page, size)

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun getById(@PathParam("id") id: Long): PessoaResponseDTO = service.getById(id)

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun update(@PathParam("id") id: Long, @Valid request: PessoaRequestDTO): PessoaResponseDTO =
        service.update(id, request)

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun delete(@PathParam("id") id: Long): Response {
        service.delete(id)
        return Response.noContent().build()
    }
}
