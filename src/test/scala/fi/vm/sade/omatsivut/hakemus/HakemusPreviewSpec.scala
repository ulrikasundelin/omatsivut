package fi.vm.sade.omatsivut.hakemus

import fi.vm.sade.omatsivut.config.AppConfig
import fi.vm.sade.omatsivut.fixtures.TestFixture._
import fi.vm.sade.omatsivut.fixtures.{FixtureImporter, TestFixture}
import fi.vm.sade.omatsivut.servlet.ApplicationsServlet
import fi.vm.sade.omatsivut.servlet.KoulutusServlet
import fi.vm.sade.omatsivut.config.ScalatraPaths
import org.xml.sax.SAXParseException

class HakemusPreviewSpec extends HakemusApiSpecification {
  override implicit lazy val appConfig = new AppConfig.IT

  sequential

  "GET /api/applications/preview/:oid" should {
    "generate application preview" in {
      FixtureImporter().applyOverrides("peruskoulu")
      authGet(ScalatraPaths.applications + "/applications/preview/" + hakemusYhteishakuKevat2014WithForeignBaseEducationId, personOid) {
        response.getContentType() must_== "text/html; charset=UTF-8"

        body must contain("""<label>Vastaanotettu</label><span>25.06.2014 15:52</span>""")
        body must contain("""<label>Hakemusnumero</label><span>00000441368</span>""")

        // henkilötiedot
        body must contain("""<div class="question"><label>Sukunimi</label><span class="answer">Testaaja</span>""")
        body must contain("""<div class="question"><label>Äidinkieli</label><span class="answer">suomi</span>""")
        body must contain("""<div class="question"><label>Lähiosoite</label><span class="answer">foobartie 1</span></div>""")
        body must contain("""<div class="question"><label></label><span class="answer">00100 HELSINKI</span></div>""")
        body.split("div").toList.count(_.contains("<label>Puhelinnumero</label>")) must_== 2
        // koulutustausta
        body must contain("""<div class="question"><label>Valitse tutkinto, jolla haet koulutukseen</label><span class="answer">Perusopetuksen oppimäärä</span>""")
        // hakutoiveet
        body must contain("""<li class="preference-row"><span class="index">1</span><span class="learning-institution"><label>Opetuspiste</label><span>Kallion lukio</span></span><span class="education"><label>Koulutus</label><span>Lukion ilmaisutaitolinja</span></span></li>""")
        // lupatiedot
        body must contain("""<label>Minulle saa lähettää postia ja sähköpostia vapaista opiskelupaikoista ja muuta koulutusmarkkinointia.</label><span class="answer">Ei</span>""")
        // harkinnanvarainen haku liitepyynnöt
        body must contain("""<td><div>Kallion lukio Lukion ilmaisutaitolinja</div><div>PL 3805</div><div>00099</div><div>HELSINGIN KAUPUNKI</div></td>""")
        body must contain("""<td><div>Salon Lukio Lukio</div><div>Kaherinkatu 2</div><div>24130</div><div>SALO</div></td>""")
      }
    }

    "support higher grade attachments" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithHigherGradeAttachments, personOid) {
        println(prettyPrintHtml(body))
        body must contain("""<div>Sturenkatu 2</div><div>00510</div><div>HELSINKI</div>""")
      }
    }

    "support application option spesific attachments" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithApplicationOptionAttachments, personOid) {
        println(prettyPrintHtml(body))
        // ApplicationOptionAttachments
        body must contain("""<td><p>Kopio ulkomaisesta tutkintotodistuksesta</p><div><p>Osoittaakseen hakukelpoisuutensa ulkomailla suoritetulla tutkintotodistuksella hakevan on toimitettava todistukset liitteiden toimitusaikataulun mukaisesti 6.10.2014 mennessä, tämä ei koske ulkomailla suoritettuja kansainvälisiä ylioppilastutkintoja.</p>
<p>Todistuskopioiden lisäksi on toimitettava kopio virallisen kielenkääntäjän suomeksi, ruotsiksi tai englanniksi tekemä käännöskopio tutkintotodistuksesta, jos tutkintotodistuksen kieli ei ole mikään näistä. Käännöksessä tulee olla kääntäjän allekirjoitus ja leima. Käännös tulee toimittaa liitteiden toimitusaikataulun mukaisesti ensisijaisen ammattikorkeakouluhakukohteen hakijapalveluihin 6.10.2014 mennessä.</p>
<p>Jos hakija on pakolainen tai pakolaiseen rinnastettavassa asemassa, eikä hän voi todistaa tutkintoaan asiakirjoin, ammattikorkeakoulu voi kutsua hakijan valintakokeeseen, siitä huolimatta, ettei ammattikorkeakoulu käytä harkinnanvaraista valintaa. Tällöin hakijalla tulee olla pakolais-statuksestaan kertova viranomaispäätös (turvapaikkapäätös tai oleskelulupa suojelun perusteella). Hakijan tulee toimittaa kopio kyseisestä päätöksestä ensisijaisen ammattikorkeakoulun hakijapalveluihin liitteiden toimitusaikataulun mukaisesti 6.10.2014 mennessä.</p></div></td><td><div>PL 4011</div><div>00079</div><div>METROPOLIA</div></td><td><div>06.10.2014 15:00</div>""")
        // ApplicationOptionAttachmentRequests
        body must contain("""<td><p>Kirjattu todistus</p></td><td><div>Juksutie 11</div><div>00100</div><div>HELSINKI</div></td><td><div>07.09.2014 23:59</div>""")
        body must contain("""<p>Maol-voitto</p><div>Lähetä kopio todistuksestasi.</div></td><td><div>Hyn osoite</div><div>00100</div><div>HELSINKI</div></td><td><div>15.08.2014 15:00</div>""")
      }
    }

    "support additional questions per preference" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithAtheleteQuestions, personOid) {
        println(prettyPrintHtml(body))
        body must contain("""<div class="question"><label>Haetko urheilijan ammatilliseen koulutukseen?</label><span class="answer">Kyllä</span></div><div class="question"><label>Haluaisitko suorittaa lukion ja/tai ylioppilastutkinnon samaan aikaan kuin ammatillisen perustutkinnon?</label><span class="answer">Kyllä</span></div>""")
      }
    }

    "support grade grid" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithGradeGridAndDancePreference, personOid) {
        body must contain("""<tr><td id="PK_A1_column1">A1-kieli</td><td id="PK_A1_column2">englanti</td><td id="PK_A1_column3">9</td><td id="PK_A1_column4">Ei arvosanaa</td><td id="PK_A1_column5">Ei arvosanaa</td></tr>""")
        body must contain("""<tr><td id="PK_MA_column1" colspan="2">Matematiikka</td><td id="PK_MA_column3">9</td><td id="PK_MA_column4">Ei arvosanaa</td><td id="PK_MA_column5">Ei arvosanaa</td></tr>""")
      }
    }

    "support grade grid from grade 10" in {
      FixtureImporter().applyOverrides("kymppiluokka")
      authGet(ScalatraPaths.applications + "/applications/preview/" + hakemusYhteishakuKevat2014WithForeignBaseEducationId, personOid) {
        body must contain("""<tr><td id="PK_B1_column1">B1-kieli</td><td id="PK_B1_column2">englanti</td><td id="PK_B1_column3">10(9)</td><td id="PK_B1_column4">Ei arvosanaa</td><td id="PK_B1_column5">Ei arvosanaa</td></tr>""")
        body must contain("""<tr><td id="PK_MA_column1" colspan="2">Matematiikka</td><td id="PK_MA_column3">10(9)</td><td id="PK_MA_column4">Ei arvosanaa</td><td id="PK_MA_column5">Ei arvosanaa</td></tr>""")
      }
    }

    "support athlete additional information" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithAtheleteQuestions, personOid) {
        println(prettyPrintHtml(body))
        body must contain("""Muistathan täyttää myös urheilijan lisätietolomakkeen ja palauttaa sen oppilaitokseen, johon haet.""")
        body must contain("""<a href="http://www.sport.fi/urheiluoppilaitoshaku" target="_blank">http://www.sport.fi/urheiluoppilaitoshaku (pdf-tiedosto, avautuu uuteen välilehteen)</a>""")
      }
    }

    "support dance additional information" in {
      authGet(ScalatraPaths.applications + "/applications/preview/" + TestFixture.hakemusWithGradeGridAndDancePreference, personOid) {
        body must contain("""Hait musiikki-, tanssi- tai liikunta-alan koulutukseen. Muista tarkistaa oppilaitoksen nettisivuilta, pitääkö sinun täyttää myös oppilaitoksen oma lisätietolomake.""")
      }
    }

  }

  private def prettyPrintHtml(content: String) = {
    try {
      val prettier = new scala.xml.PrettyPrinter(80, 4)
      prettier.format(scala.xml.XML.loadString(content))
    }
    catch {
      case e: SAXParseException => {
        e.printStackTrace()
        content
      }
    }
  }

  addServlet(new ApplicationsServlet(), ScalatraPaths.applications)
  addServlet(new KoulutusServlet(), ScalatraPaths.koulutusinformaatio)
}
