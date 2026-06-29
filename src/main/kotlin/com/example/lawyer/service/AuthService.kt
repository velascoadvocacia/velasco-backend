package com.example.lawyer.service

import com.example.lawyer.dto.request.AuthLoginRequest
import com.example.lawyer.dto.response.AuthLoginResponse
import com.example.lawyer.exception.UnauthorizedException
import com.example.lawyer.mapper.UsuarioMapper
import io.quarkus.elytron.security.common.BcryptUtil
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.Duration
import java.time.Instant

@ApplicationScoped
class AuthService(
    private val usuarioService: UsuarioService,
    private val usuarioMapper: UsuarioMapper,
    @ConfigProperty(name = "law-office.jwt.issuer")
    private val issuer: String,
    @ConfigProperty(name = "law-office.jwt.expires-in-seconds", defaultValue = "3600")
    private val expiresInSeconds: Long
) {
    private val logger = Logger.getLogger(AuthService::class.java)

    fun login(request: AuthLoginRequest): AuthLoginResponse {
        val usuario = usuarioService.findActiveByUsername(request.username!!)
            ?: throw UnauthorizedException()

        if (!BcryptUtil.matches(request.senha!!, usuario.senha)) {
            logger.warnf("Falha de login para username=%s", request.username)
            throw UnauthorizedException()
        }

        val now = Instant.now()
        val token = Jwt.issuer(issuer)
            .upn(usuario.username)
            .subject(usuario.id.toString())
            .groups(setOf(usuario.perfil.name))
            .claim("perfil", usuario.perfil.name)
            .claim("pessoaId", usuario.pessoa!!.id)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofSeconds(expiresInSeconds)))
            .sign()

        logger.infof("Login realizado username=%s", usuario.username)
        return AuthLoginResponse(token = token, expiresIn = expiresInSeconds, usuario = usuarioMapper.toResponse(usuario))
    }
}
