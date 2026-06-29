package com.example.lawyer.security

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.repository.PessoaRepository
import com.example.lawyer.repository.UsuarioRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.LocalDate

@ApplicationScoped
class AdminBootstrap(
    private val pessoaRepository: PessoaRepository,
    private val usuarioRepository: UsuarioRepository,
    @ConfigProperty(name = "law-office.bootstrap.admin.enabled", defaultValue = "false")
    private val enabled: Boolean,
    @ConfigProperty(name = "law-office.bootstrap.admin.username", defaultValue = "admin")
    private val username: String,
    @ConfigProperty(name = "law-office.bootstrap.admin.password", defaultValue = "Admin@123456")
    private val password: String
) {
    private val logger = Logger.getLogger(AdminBootstrap::class.java)

    fun onStart(@Observes event: StartupEvent) {
        if (!enabled) return

        QuarkusTransaction.requiringNew().run {
            if (usuarioRepository.count() > 0) return@run

            val pessoa = Pessoa(
                nome = "Administrador",
                cpf = "52998224725",
                email = "admin@law-office.local",
                telefone = null,
                tipoPessoa = TipoPessoa.FISICA,
                dataNascimento = LocalDate.of(1990, 1, 1),
                ativo = true
            )
            pessoaRepository.persist(pessoa)

            val usuario = Usuario(
                username = username.trim().lowercase(),
                senha = BcryptUtil.bcryptHash(password),
                pessoa = pessoa,
                perfil = PerfilUsuario.ADMIN,
                ativo = true
            )
            usuarioRepository.persist(usuario)
            logger.warnf("Usuario administrador inicial criado username=%s", usuario.username)
        }
    }
}
