package com.example.lawyer.service

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TratamentoAdvogado
import com.example.lawyer.domain.enums.Sexo
import com.example.lawyer.domain.model.Endereco
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.request.ProcuracaoExportRequest
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.repository.UsuarioRepository
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@ApplicationScoped
class ProcuracaoExportService(
    private val processoService: ProcessoService,
    private val pessoaService: PessoaService,
    private val usuarioRepository: UsuarioRepository,
    @ConfigProperty(name = "rt.escritorio.endereco")
    private val enderecoEscritorio: String
) {
    fun generate(request: ProcuracaoExportRequest): ProcuracaoGerada {
        val processo = request.processoId?.let(processoService::findEntity)
        val reclamante = resolvePessoas(request.reclamantesIds, processo?.reclamantes?.toList()).firstOrNull()
        val reclamada = resolvePessoas(request.reclamadasIds, processo?.reclamadas?.toList()).firstOrNull()
        val advogados = resolveAdvogados(request.advogadosIds, processo?.advogados?.toList())
        val dados = DadosDocumento(reclamante, reclamada, advogados, LocalDate.now())

        val bytes = XWPFDocument().use { document ->
            configurePage(document)
            addHeaderAndFooter(document)
            createProcuracao(document, dados)
            addPageBreak(document)
            createContrato(document, dados)
            addPageBreak(document)
            createDeclaracao(document, dados)
            ByteArrayOutputStream().use { output ->
                document.write(output)
                output.toByteArray()
            }
        }
        return ProcuracaoGerada(reclamante?.nome.clean() ?: "documento", bytes)
    }

    private fun createProcuracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "PROCURAÇÃO AD JUDICIA")
        spacer(document)
        qualificationParagraph(document, "OUTORGANTE:", dados.qualificacaoReclamante(comNascimento = true))
        spacer(document)
        labeledParagraph(document, "OUTORGADOS:", dados.listaOutorgados())
        labeledParagraph(
            document,
            "PODERES:",
            "Outorga(m) os mais amplos e gerais poderes da cláusula   “ad judicia et extra” para o foro em geral, qualquer instância ordinária ou extraordinária, na qual o autor ou réu litisconsorte ativo ou passivo, denunciado ou podendo ainda, utilizar dos poderes especiais para firmar compromisso de qualquer   natureza,   concordar   com contas e cálculos, transigir, variar, fazer acordos, receber e dar quitação, receber alvará judicial e guias de retirada, desistir, renunciar ao direito sobre o que se funda a ação, podendo ainda receber importância depositadas em nome do outorgante à conta bancaria referente ao processo encaminhado com o presente, em quaisquer agência bancaria, podendo atuar em conjunto ou separadamente, bem como substabelecer com ou sem reserva de poderes."
        )
        labeledParagraph(document, "PODERES ESPECIAIS:", dados.poderesEspeciais())
        spacer(document)
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        repeat(2) { spacer(document) }
        signature(document, dados.nomeReclamante())
    }

    private fun createContrato(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "CONTRATO DE HONORÁRIOS")
        contractOpening(document, dados)
        body(document, "1ª - Os CONSTITUÍDOS se comprometem em cumprimento ao mandato recebido, a patrocinar a causa do (a) CONSTITUINTE, que consiste em ${dados.acaoContraReclamada()}.")
        body(document, "2ª - Em contraprestação, o (a/s) CONSTITUINTE se compromete a remunerar os serviços profissionais dos CONSTITUÍDOS na importância correspondente a 30% (trinta por cento) dos valores que vier a receber, a título de acordos ou liquidação de sentença, inclusive sobre valores de FGTS e do seguro desemprego. Em caso de acordo realizado diretamente entre autor e réu, sem anuência do CONSTITUIDO, o percentual de honorários incidirá sobre o valor dos pedidos constantes na petição inicial. Fica ajustado que haverá acréscimo de 5% (cinco por cento) sobre os honorários anteriormente pactuados na hipótese de efetiva atuação em grau recursal, consistente na elaboração, interposição ou acompanhamento de recursos contra decisões proferidas em 1º grau, a serem realizados por escritório parceiro sediado na cidade de Curitiba.")
        body(document, "3ª - Os percentuais retros pactuados são independentes de honorários advocatícios ou assistências que venham a ser deferidos em sentença.")
        body(document, "4ª - Os honorários e valores constantes nas cláusulas 2ª, 3ª e 4ª, deste contrato, ficam ajustados como valor líquido, certo e exigível para efeito de execução, valendo o presente instrumento como título de crédito (CPC, art. 586 c/c art. 585, I e art. 24 da lei 8906/94).")
        body(document, "5ª. – Todas à custa e despesas ligadas direta ou indiretamente com o processo, incluindo-se custas iniciais, taxas, honorários periciais, assistência técnica, fotocópias, emolumentos, viagens, portes, honorários de sucumbência, etc., ficarão a cargo do CONTRATANTE, que disponibilizará o numerário necessário no decorrer do processo ou ao final do mesmo, conforme o caso.")
        body(document, "6ª. – O não comparecimento injustificado do(a) CONTRATANTE às audiências designadas, bem como qualquer conduta que resulte na desistência da ação ou inviabilize o regular andamento do processo, autoriza a rescisão do presente contrato por culpa exclusiva do(a) CONTRATANTE. Nessas hipóteses, será devida aos CONSTITUÍDOS, a título de cláusula penal, multa equivalente a 01 (um) salário mínimo vigente, sem prejuízo do pagamento dos honorários proporcionais pelos serviços já prestados e das despesas eventualmente suportadas.")
        body(document, "7ª. - Fica eleito o foro de Cascavel - PR para dirimir quaisquer dúvidas acerca deste contrato, prevalecendo sobre qualquer outro por mais privilegiado que for.")
        body(document, "E por estarem assim justos e contratados, obrigam-se a cumprir todas as disposições do presente instrumento que assinam na presença das testemunhas abaixo firmadas, para que surta seus jurídicos e legais efeitos.")
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        spacer(document)
        signature(document, "${dados.nomeReclamante()}                                      ADVOGADO:")
        spacer(document)
        body(document, "TESTEMUNHAS", bold = true, alignment = ParagraphAlignment.CENTER)
    }

    private fun createDeclaracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "DECLARAÇÃO DE HIPOSSUFICIÊNCIA")
        spacer(document)
        body(
            document,
            "Eu, ${dados.qualificacaoReclamante(comNascimento = false).text}, declaro para os devidos fins, de direito sob pena da lei, ser pessoa pobre não tendo condições de arcar com despesas e custas processuais, sem prejuízo de minha subsistência e de meus dependentes."
        )
        body(document, "Por ser expressão da verdade firmo o presente.")
        spacer(document)
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        repeat(2) { spacer(document) }
        signature(document, dados.nomeReclamante())
    }

    private fun title(document: XWPFDocument, text: String) {
        val paragraph = document.createParagraph().apply(::centerOnPage)
        paragraph.createRun().apply {
            setText(text)
            isBold = true
            underline = UnderlinePatterns.SINGLE
            fontFamily = FONT
            fontSize = 12
        }
    }

    private fun labeledParagraph(
        document: XWPFDocument,
        label: String,
        value: String,
        boldValue: Boolean = false
    ) {
        val paragraph = baseParagraph(document)
        run(paragraph, label, bold = true)
        run(paragraph, " $value", bold = boldValue)
    }

    private fun qualificationParagraph(document: XWPFDocument, label: String, qualification: Qualification) {
        val paragraph = baseParagraph(document)
        run(paragraph, label, bold = true)
        qualification.name.takeIf { it.isNotBlank() }?.let { run(paragraph, " ${it.uppercase(PT_BR)}", bold = true) }
        qualification.details.takeIf { it.isNotBlank() }?.let { run(paragraph, ", $it") }
    }

    private fun contractOpening(document: XWPFDocument, dados: DadosDocumento) {
        val paragraph = baseParagraph(document)
        run(paragraph, "Por um lado, CONTRATADO: ")
        run(paragraph, dados.primeiroAdvogado())
        run(paragraph, ", estabelecido na $enderecoEscritorio, e de outro lado, CONTRATANTE: o (a) ")
        val qualification = dados.qualificacaoReclamante(comNascimento = true)
        qualification.name.takeIf { it.isNotBlank() }?.let { run(paragraph, it.uppercase(PT_BR), bold = true) }
        qualification.details.takeIf { it.isNotBlank() }?.let { run(paragraph, ", $it") }
        run(paragraph, ", doravante denominado (a/s) de CONSTITUINTE, convencionam e contratam as cláusulas e condições seguintes:")
    }

    private fun body(
        document: XWPFDocument,
        text: String,
        bold: Boolean = false,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH
    ) {
        val paragraph = baseParagraph(document).apply { this.alignment = alignment }
        run(paragraph, text, bold)
    }

    private fun signature(document: XWPFDocument, text: String) {
        body(document, text, alignment = ParagraphAlignment.CENTER)
    }

    private fun baseParagraph(document: XWPFDocument): XWPFParagraph = document.createParagraph().apply {
        alignment = ParagraphAlignment.BOTH
        spacingAfter = 80
        val spacing = ctp.pPr?.spacing ?: ctp.addNewPPr().addNewSpacing()
        spacing.line = BigInteger.valueOf(240)
        spacing.lineRule = STLineSpacingRule.AUTO
    }

    private fun run(paragraph: XWPFParagraph, text: String, bold: Boolean = false) {
        paragraph.createRun().apply {
            setText(text)
            isBold = bold
            fontFamily = FONT
            fontSize = 10
        }
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph().apply { spacingAfter = 0 }
    }

    private fun addPageBreak(document: XWPFDocument) {
        document.createParagraph().createRun().addBreak(BreakType.PAGE)
    }

    private fun configurePage(document: XWPFDocument) {
        val section = document.document.body.addNewSectPr()
        section.pgSz = CTPageSz.Factory.newInstance().apply {
            w = BigInteger.valueOf(11_910)
            h = BigInteger.valueOf(16_840)
            orient = STPageOrientation.PORTRAIT
        }
        section.pgMar = CTPageMar.Factory.newInstance().apply {
            top = BigInteger.valueOf(1_620)
            right = BigInteger.valueOf(1_160)
            bottom = BigInteger.valueOf(2_040)
            left = BigInteger.valueOf(1_160)
            header = BigInteger.valueOf(159)
            footer = BigInteger.valueOf(1_846)
            gutter = BigInteger.ZERO
        }
    }

    private fun addHeaderAndFooter(document: XWPFDocument) {
        val policy = document.createHeaderFooterPolicy()
        val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
        val headerParagraph = (header.paragraphs.firstOrNull() ?: header.createParagraph()).apply(::centerOnPage)
        val headerSize = proportionalImageSize(HEADER_IMAGE_WIDTH_PX, HEADER_IMAGE_HEIGHT_PX)
        javaClass.getResourceAsStream("/assets/header_velasco.jpeg")!!.use { input ->
            headerParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_JPEG, "header_velasco.jpeg",
                headerSize.widthEmu, headerSize.heightEmu
            )
        }
        val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
        val footerParagraph = (footer.paragraphs.firstOrNull() ?: footer.createParagraph()).apply(::centerOnPage)
        val footerSize = proportionalImageSize(FOOTER_IMAGE_WIDTH_PX, FOOTER_IMAGE_HEIGHT_PX)
            .scaled(FOOTER_SCALE_PERCENT)
        javaClass.getResourceAsStream("/assets/footer_velasco.png")!!.use { input ->
            footerParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_PNG, "footer_velasco.png",
                footerSize.widthEmu, footerSize.heightEmu
            )
        }
    }

    private fun centerOnPage(paragraph: XWPFParagraph) {
        paragraph.alignment = ParagraphAlignment.CENTER
        paragraph.indentationLeft = 0
        paragraph.indentationRight = 0
        paragraph.indentationFirstLine = 0
    }

    private fun proportionalImageSize(originalWidthPx: Int, originalHeightPx: Int): ImageSize {
        val heightAtMaximumWidth = IMAGE_MAX_WIDTH_EMU.toLong() * originalHeightPx / originalWidthPx
        return if (heightAtMaximumWidth <= IMAGE_MAX_HEIGHT_EMU) {
            ImageSize(IMAGE_MAX_WIDTH_EMU, heightAtMaximumWidth.toInt())
        } else {
            ImageSize(
                widthEmu = (IMAGE_MAX_HEIGHT_EMU.toLong() * originalWidthPx / originalHeightPx).toInt(),
                heightEmu = IMAGE_MAX_HEIGHT_EMU
            )
        }
    }

    private fun resolvePessoas(ids: List<Long>, fallback: List<Pessoa>?): List<Pessoa> =
        if (ids.isNotEmpty()) ids.distinct().map(pessoaService::findEntity) else fallback.orEmpty()

    private fun resolveAdvogados(ids: List<Long>, fallback: List<Usuario>?): List<Usuario> =
        if (ids.isNotEmpty()) {
            ids.distinct().map { id ->
                usuarioRepository.findActiveById(id) ?: throw ResourceNotFoundException("Advogado $id não encontrado")
            }
        } else fallback.orEmpty().filter { it.ativo }

    private inner class DadosDocumento(
        private val reclamante: Pessoa?,
        private val reclamada: Pessoa?,
        private val advogados: List<Usuario>,
        val dataExportacao: LocalDate
    ) {
        fun nomeReclamante() = reclamante?.nome.clean().orEmpty()
        fun nomeReclamada() = reclamada?.nome.clean().orEmpty()

        fun qualificacaoReclamante(comNascimento: Boolean): Qualification {
            val pessoa = reclamante
            val feminino = isFeminino(pessoa)
            val parts = mutableListOf<String>()
            pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.let(parts::add)
            formatEstadoCivil(pessoa?.estadoCivil, feminino)?.let(parts::add)
            pessoa?.cpf.clean()?.let { parts += "inscrito(a) no CPFMF sob n.º $it" }
            formatRg(pessoa)?.let { parts += "e no RG sob o n.º $it" }
            if (comNascimento) pessoa?.dataNascimento?.let { parts += "nascido(a) em ${formatLongDate(it)}" }
            formatResidence(pessoa?.endereco)?.let(parts::add)
            pessoa?.telefone.clean()?.let { parts += "telefone $it" }
            return Qualification(nomeReclamante(), parts.joinToString(", ").removePrefix("e "))
        }

        fun listaOutorgados(): String = advogados.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { formatAdvogado(it) }
            ?.plus(", todos estabelecidos na $enderecoEscritorio.")
            ?: "Todos estabelecidos na $enderecoEscritorio."

        fun primeiroAdvogado(): String = advogados.firstOrNull()?.let(::formatAdvogado).orEmpty()
        fun poderesEspeciais(): String = "Para o fim especial de ${acaoContraReclamada()}."
        fun acaoContraReclamada(): String = nomeReclamada().takeIf { it.isNotBlank() }
            ?.let { "propor Reclamação Trabalhista em face de $it e outros" }
            ?: "propor Reclamação Trabalhista"
    }

    private fun formatAdvogado(usuario: Usuario): String {
        val pessoa = usuario.pessoa
        val generoFeminino = pessoa?.sexo == Sexo.FEMININO || usuario.tratamento == TratamentoAdvogado.DRA
        val profissao = if (generoFeminino) "advogada" else "advogado"
        val inscrito = if (generoFeminino) "inscrita" else "inscrito"
        val parts = mutableListOf<String>()
        pessoa?.nome.clean()?.uppercase(PT_BR)?.let(parts::add)
        pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.let(parts::add)
        formatEstadoCivil(pessoa?.estadoCivil, generoFeminino)?.let(parts::add)
        parts += profissao
        val oab = formatOab(usuario)
        if (oab != null) parts += "$inscrito na $oab"
        return parts.joinToString(", ")
    }

    private fun formatRg(pessoa: Pessoa?): String? = pessoa?.rg.clean()?.let { rg ->
        listOfNotNull(rg, pessoa?.orgaoEmissorRg.clean()).joinToString(" ")
    }

    private fun formatResidence(endereco: Endereco?): String? {
        endereco ?: return null
        val location = listOfNotNull(endereco.cidade.clean(), endereco.estado.clean())
            .joinToString(" – ").takeIf { it.isNotBlank() }
        val streetAndNumber = listOfNotNull(endereco.rua.clean(), endereco.numero.clean())
            .joinToString(", ").takeIf { it.isNotBlank() }
        val address = listOfNotNull(
            streetAndNumber?.let { base -> endereco.complemento.clean()?.let { "$base – $it" } ?: base },
            endereco.bairro.clean()?.let { "Bairro $it" },
            formatCep(endereco.cep)?.let { "CEP $it" }
        ).joinToString(", ")
        val complete = listOfNotNull(location, address.takeIf { it.isNotBlank() }).joinToString(", ")
        return complete.takeIf { it.isNotBlank() }?.let { "residente e domiciliado(a) em $it" }
    }

    private fun formatCep(value: String?): String? {
        val digits = value?.filter(Char::isDigit).orEmpty()
        return if (digits.length == 8) "${digits.take(5)}-${digits.drop(5)}" else value.clean()
    }

    private fun formatEstadoCivil(value: EstadoCivil?, feminino: Boolean): String? = when (value) {
        EstadoCivil.SOLTEIRO -> if (feminino) "solteira" else "solteiro"
        EstadoCivil.CASADO -> if (feminino) "casada" else "casado"
        EstadoCivil.DIVORCIADO -> if (feminino) "divorciada" else "divorciado"
        EstadoCivil.VIUVO -> if (feminino) "viúva" else "viúvo"
        EstadoCivil.UNIAO_ESTAVEL -> "união estável"
        EstadoCivil.SEPARADO -> if (feminino) "separada" else "separado"
        null -> null
    }

    private fun isFeminino(pessoa: Pessoa?): Boolean = pessoa?.sexo == Sexo.FEMININO ||
        (pessoa?.sexo == null && pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.endsWith("a") == true)

    private fun formatOab(usuario: Usuario): String? {
        val uf = usuario.ufOab.clean()
        val numero = usuario.numeroOab.clean()
        return when {
            uf != null && numero != null -> "OAB/$uf nº $numero"
            uf != null -> "OAB/$uf"
            numero != null -> "OAB nº $numero"
            else -> null
        }
    }

    private fun formatLongDate(date: LocalDate): String = date.format(LONG_DATE_FORMATTER)
    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private data class Qualification(val name: String, val details: String) {
        val text: String = listOf(name, details).filter(String::isNotBlank).joinToString(", ")
    }

    private data class ImageSize(val widthEmu: Int, val heightEmu: Int)

    private fun ImageSize.scaled(percent: Int): ImageSize = ImageSize(
        widthEmu = widthEmu * percent / 100,
        heightEmu = heightEmu * percent / 100
    )

    data class ProcuracaoGerada(val nomeReclamante: String, val bytes: ByteArray)

    private companion object {
        const val FONT = "Georgia"
        const val CIDADE_ESCRITORIO = "Cascavel"
        const val IMAGE_MAX_WIDTH_EMU = 7_543_800
        const val IMAGE_MAX_HEIGHT_EMU = 1_044_713
        const val HEADER_IMAGE_WIDTH_PX = 2_484
        const val HEADER_IMAGE_HEIGHT_PX = 344
        const val FOOTER_IMAGE_WIDTH_PX = 670
        const val FOOTER_IMAGE_HEIGHT_PX = 202
        const val FOOTER_SCALE_PERCENT = 75
        val PT_BR: Locale = Locale("pt", "BR")
        val LONG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    }
}
