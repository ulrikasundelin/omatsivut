package fi.vm.sade.omatsivut.servlet

import fi.vm.sade.omatsivut.AppConfig.AppConfig
import fi.vm.sade.omatsivut.security._
import org.scalatra.{Cookie, CookieOptions}
import org.scalatra.servlet.RichResponse
import scala.collection.JavaConverters._
import fi.vm.sade.omatsivut.auditlog.AuditLogger

class SessionServlet(implicit val appConfig: AppConfig) extends OmatSivutServletBase with AuthCookieParsing {
  get("/initsession") {
    request.getHeaderNames.asScala.toList.map(h => logger.info(h + ": " + request.getHeader(h)))
    createAuthCookieCredentials match {
      case Some(credentials) => createAuthCookieResponse(credentials)
      case _ => response.redirect(appConfig.authContext.ssoContextPath + "/Shibboleth.sso/LoginFI") //TODO Localization
    }
  }

  private def findHetuFromParams = {
    headerOption("Hetu") match {
      case Some(hetu) => Some(hetu)
      case None if appConfig.usesFakeAuthentication => paramOption("hetu")
      case _ => None
    }
  }

  private def createAuthCookieCredentials: Option[CookieCredentials] = {
    checkCredentials match {
      case Some((oid, cookie)) => {
        AuditLogger.logCreateSession(oid, cookie)
        Some(CookieCredentials(oid, cookie))
      }
      case _ => None
    }
  }

  private def checkCredentials = {
    for {
      hetu <- findHetuFromParams
      cookie <- shibbolethCookieInRequest(request)
      oid <- AuthenticationInfoService.apply.getHenkiloOID(hetu)
    } yield (oid, cookie)
  }

  private def createAuthCookieResponse(credentials: CookieCredentials) {
    val encryptedCredentials = AuthenticationCipher().encrypt(credentials.toString)
    response.addCookie(Cookie("auth", encryptedCredentials)(appConfig.authContext.cookieOptions))
    logger.info("Redirecting to " + redirectUri)
    response.redirect(redirectUri)
  }

  private def redirectUri: String = {
    request.getContextPath + paramOption("redirect").getOrElse("/index.html")
  }
}