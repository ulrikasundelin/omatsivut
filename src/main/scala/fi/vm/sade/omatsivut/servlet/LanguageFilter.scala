package fi.vm.sade.omatsivut.servlet

import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}

import fi.vm.sade.hakemuseditori.domain.Language
import fi.vm.sade.utils.slf4j.Logging
import org.scalatra.ScalatraFilter

class LanguageFilter extends ScalatraFilter with Logging{
  val cookieName = "i18next"
  val cookieMaxAge = 60 * 60 * 24 * 1800

  before() {
    checkLanguage(request, response)
  }

  private def checkLanguage(request: HttpServletRequest, response: HttpServletResponse) {
    val (lang: Language.Language, setCookie: Boolean) = chooseLanguage(Option(request.getParameter("lang")), Option(request.getCookies()))
    if(setCookie) {
      addCookie(response, lang)
    }
    request.setAttribute("lang", lang)
  }

  def chooseLanguage(paramVal: Option[String], cookies: Option[Array[Cookie]]): (Language.Language, Boolean) = {
    paramVal match {
      case Some(langStr) => {
        Language.parse(langStr) match {
          case Some(lang) => (lang, true)
          case None => {
            logger.warn("Unsupported language '" + langStr + "' using 'fi' instead")
            (Language.fi, true)
          }
        }
      }
      case None => (langCookie(cookies).getOrElse(Language.fi), true)
    }
  }

  private def langCookie(cookies: Option[Array[Cookie]]) = {
    reqCookie(cookies, {_.getName.equals(cookieName)})
  }

  private def reqCookie(optCookies: Option[Array[Cookie]], matcher: (Cookie) => Boolean) = {
    for {
      cookies <- optCookies
      cookie <- cookies.find(matcher)
      lang <- Language.parse(cookie.getValue())
    } yield lang
  }

  private def addCookie(response: HttpServletResponse, lang: Language.Language) {
    val cookie = new Cookie(cookieName, lang.toString())
    cookie.setMaxAge(cookieMaxAge)
    cookie.setPath("/")
    response.addCookie(cookie)
  }
}