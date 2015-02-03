package fi.vm.sade.omatsivut.config

import java.util.concurrent.Executors

import fi.vm.sade.groupemailer.{GroupEmailService, GroupEmailComponent}
import fi.vm.sade.hakemuseditori.HakemusEditoriComponent
import fi.vm.sade.hakemuseditori.hakemus._
import fi.vm.sade.hakemuseditori.localization.TranslationsComponent
import fi.vm.sade.hakemuseditori.auditlog.{AuditContext, AuditLogger, AuditLoggerComponent}
import fi.vm.sade.omatsivut.hakemuspreview.HakemusPreviewGeneratorComponent
import fi.vm.sade.omatsivut.localization.OmatSivutTranslations
import fi.vm.sade.utils.captcha.{CaptchaServiceSettings, CaptchaServiceComponent}
import fi.vm.sade.omatsivut.config.AppConfig._
import fi.vm.sade.hakemuseditori.domain.Language.Language
import fi.vm.sade.omatsivut.fixtures.hakemus.ApplicationFixtureImporter
import fi.vm.sade.hakemuseditori.koodisto.{RemoteKoodistoService, StubbedKoodistoService, KoodistoComponent, KoodistoService}
import fi.vm.sade.hakemuseditori.koulutusinformaatio.{KoulutusInformaatioComponent, KoulutusInformaatioService}
import fi.vm.sade.hakemuseditori.lomake.{LomakeRepository, LomakeRepositoryComponent}
import fi.vm.sade.omatsivut.muistilista.MuistilistaServiceComponent
import fi.vm.sade.hakemuseditori.ohjausparametrit.{OhjausparametritComponent, OhjausparametritService}
import fi.vm.sade.omatsivut.servlet._
import fi.vm.sade.omatsivut.servlet.session.{LogoutServletContainer, SecuredSessionServletContainer}
import fi.vm.sade.hakemuseditori.tarjonta.{TarjontaComponent, TarjontaService}
import fi.vm.sade.hakemuseditori.valintatulokset._

class ComponentRegistry(val config: AppConfig)
  extends SpringContextComponent with
          TranslationsComponent with
          MuistilistaServiceComponent with
          GroupEmailComponent with
          KoulutusInformaatioComponent with
          OhjausparametritComponent with
          LomakeRepositoryComponent with
          HakemusRepositoryComponent with
          ValintatulosServiceComponent with
          AuditLoggerComponent with
          ApplicationValidatorComponent with
          HakemusPreviewGeneratorComponent with
          HakemusConverterComponent with
          HakemusEditoriComponent with
          ApplicationsServletContainer with
          MuistilistaServletContainer with
          CaptchaServiceComponent with
          KoulutusServletContainer with
          SecuredSessionServletContainer with
          LogoutServletContainer with
          FixtureServletContainer with
          KoodistoServletContainer with
          TarjontaComponent with
          KoodistoComponent {

  implicit val swagger = new OmatSivutSwagger

  private def configureOhjausparametritService: OhjausparametritService = config match {
    case _: StubbedExternalDeps => new StubbedOhjausparametritService()
    case _ => CachedRemoteOhjausparametritService(config.settings.ohjausparametritUrl)
  }

  private def configureKoulutusInformaatioService: KoulutusInformaatioService = config match {
    case x: StubbedExternalDeps => new StubbedKoulutusInformaatioService
    case _ => CachedKoulutusInformaatioService(new RemoteKoulutusService(config.settings.koulutusinformaatioAoUrl, config.settings.koulutusinformaationBIUrl, config.settings.koulutusinformaatioLopUrl))
  }

  private def configureGroupEmailService: GroupEmailService = config match {
    case x: StubbedExternalDeps => new FakeGroupEmailService
    case _ => new RemoteGroupEmailService(config.settings)
  }

  private def configureValintatulosService: ValintatulosService = config match {
    case x: StubbedExternalDeps => new FailingRemoteValintatulosService(config.settings.valintaTulosServiceUrl)
    case _ => new RemoteValintatulosService(config.settings.valintaTulosServiceUrl)
  }

  private def configureTarjontaService: TarjontaService = config match {
    case _: StubbedExternalDeps => new StubbedTarjontaService()
    case _ => CachedRemoteTarjontaService(config.settings.tarjontaUrl)
  }

  private def configureKoodistoService: KoodistoService = config match {
    case _: StubbedExternalDeps => new StubbedKoodistoService
    case _ => new RemoteKoodistoService(config.settings.koodistoUrl, springContext)
  }

  private lazy val runningLogger = new RunnableLogger
  private lazy val pool = Executors.newSingleThreadExecutor
  lazy val springContext = new HakemusSpringContext(OmatSivutSpringContext.createApplicationContext(config))
  val koulutusInformaatioService: KoulutusInformaatioService = configureKoulutusInformaatioService
  val ohjausparametritService: OhjausparametritService = configureOhjausparametritService
  val valintatulosService: ValintatulosService = configureValintatulosService
  val auditLogger: AuditLogger = new AuditLoggerFacade(runningLogger)
  val lomakeRepository: LomakeRepository = new RemoteLomakeRepository
  val hakemusConverter: HakemusConverter = new HakemusConverter
  val tarjontaService: TarjontaService = configureTarjontaService
  val koodistoService: KoodistoService = configureKoodistoService
  val groupEmailService: GroupEmailService = configureGroupEmailService
  val captchaService: CaptchaService = new RemoteCaptchaService(config.settings.captchaSettings)

  def muistilistaService(language: Language): MuistilistaService = new MuistilistaService(language)
  def newApplicationValidator: ApplicationValidator = new ApplicationValidator
  def newHakemusPreviewGenerator(language: Language): HakemusPreviewGenerator = new HakemusPreviewGenerator(language)
  def newApplicationsServlet = new ApplicationsServlet(config)
  def newKoulutusServlet = new KoulutusServlet
  def newSecuredSessionServlet = new SecuredSessionServlet(config.authContext)
  def newLogoutServlet = new LogoutServlet(config.authContext)
  def newFixtureServlet = new FixtureServlet(config)
  def newSwaggerServlet = new SwaggerServlet
  def newKoodistoServlet = new KoodistoServlet
  def newMuistilistaServlet = new MuistilistaServlet(config)

  def start {
    try {
      config.onStart
      pool.execute(runningLogger)
      if (config.isInstanceOf[IT]) {
        new ApplicationFixtureImporter(springContext).applyFixtures()
      }
    } catch {
      case e: Exception =>
        stop
        throw e
    }
  }

  def stop {
    config.onStop
  }

  override val translations = OmatSivutTranslations

  override val auditContext: AuditContext = new AuditContext {
    override def systemName = "omatsivut"
  }
}
