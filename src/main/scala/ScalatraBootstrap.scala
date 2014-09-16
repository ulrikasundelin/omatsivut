import javax.servlet.ServletContext
import fi.vm.sade.omatsivut.config.{OmatSivutSpringContext, AppConfig}
import AppConfig.AppConfig
import fi.vm.sade.omatsivut.servlet._
import fi.vm.sade.omatsivut.servlet.session.{SessionServlet, LoginServlet}
import fi.vm.sade.omatsivut.servlet.testing.{FakeShibbolethServlet, TestHelperServlet}
import org.scalatra._
import fi.vm.sade.omatsivut.config.ScalatraPaths

class ScalatraBootstrap extends LifeCycle {
  val config: AppConfig = AppConfig.fromSystemProperty
  OmatSivutSpringContext.check

  override def init(context: ServletContext) {
    config.start

    context.mount(config.componentRegistry.newApplicationsServlet, ScalatraPaths.applications)
    context.mount(new TranslationServlet, "/translations")
    context.mount(config.componentRegistry.newKoulutusServlet, ScalatraPaths.koulutusinformaatio)
    context.mount(config.componentRegistry.newSwaggerServlet, "/swagger/*")
    context.mount(config.componentRegistry.newSecuredSessionServlet, "/secure")
    context.mount(new SessionServlet(config), "/session")
    context.mount(new RaamitServlet(config), "/raamit")
    context.mount(new LoginServlet(config), "/login")
    context.mount(config.componentRegistry.newLogoutServlet, "/logout")
    context.mount(new TestHelperServlet(config), "/util")
    context.mount(new FakeShibbolethServlet(config), "/Shibboleth.sso")
  }

  override def destroy(context: ServletContext) = {
    config.stop
    super.destroy(context)
  }
}
