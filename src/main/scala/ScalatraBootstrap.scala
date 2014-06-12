import fi.vm.sade.omatsivut._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new OHPSwagger

  override def init(context: ServletContext) {
    context.mount(new OHPServlet, "/api")
    context.mount(new ResourcesApp, "/api-docs/*")
  }
}