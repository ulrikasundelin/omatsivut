package fi.vm.sade.omatsivut.security

import fi.vm.sade.omatsivut.config.RemoteApplicationConfig
import fi.vm.sade.omatsivut.http.DefaultHttpClient
import fi.vm.sade.omatsivut.util.Logging
import org.json4s._
import org.json4s.jackson.JsonMethods._

trait AuthenticationInfoComponent {
  val authenticationInfoService: AuthenticationInfoService

  class RemoteAuthenticationInfoService(val config: RemoteApplicationConfig, val casTicketUrl: String) extends AuthenticationInfoService with Logging with CasTicketRequiring {
    implicit val formats = DefaultFormats

    def getHenkiloOID(hetu : String) : Option[String] = {
      withServiceTicket(serviceTicket => {
        val path: String = config.url + "/" + config.config.getString("get_oid.path") + "/" + hetu
        val (responseCode, headersMap, resultString) = DefaultHttpClient.httpGet(path)
          .param("ticket", serviceTicket)
          .responseWithHeaders

        responseCode match {
          case 404 => None
          case 200 => {
            val json = parse(resultString)
            val oids: List[String] = for {
              JObject(child) <- json
              JField("oidHenkilo", JString(oid)) <- child
            } yield oid
            oids.headOption
          }
          case code => {
            logger.error("Error fetching personOid. Response code=" + code + ", content=" + resultString)
            None
          }
        }
      })
    }
  }
}



trait AuthenticationInfoService extends Logging {
  def getHenkiloOID(hetu : String) : Option[String]
}
