package fi.vm.sade.omatsivut.hakemus

import fi.vm.sade.haku.oppija.hakemus.domain.Application
import fi.vm.sade.haku.virkailija.lomakkeenhallinta.util.OppijaConstants
import fi.vm.sade.omatsivut.auditlog._
import fi.vm.sade.omatsivut.config.SpringContextComponent
import fi.vm.sade.omatsivut.domain.Language
import fi.vm.sade.omatsivut.domain.Language.Language
import fi.vm.sade.omatsivut.hakemus.domain._
import fi.vm.sade.omatsivut.lomake.LomakeRepositoryComponent
import fi.vm.sade.omatsivut.lomake.domain.Lomake
import fi.vm.sade.omatsivut.ohjausparametrit.OhjausparametritComponent
import fi.vm.sade.omatsivut.tarjonta.TarjontaComponent
import fi.vm.sade.omatsivut.tarjonta.domain.Haku
import fi.vm.sade.omatsivut.util.Timer._
import fi.vm.sade.omatsivut.valintatulokset.ValintatulosServiceComponent
import org.joda.time.LocalDateTime

import scala.util.{Failure, Success, Try}

trait HakemusRepositoryComponent {
  this: LomakeRepositoryComponent with ApplicationValidatorComponent with HakemusConverterComponent with SpringContextComponent with AuditLoggerComponent with TarjontaComponent with OhjausparametritComponent with ValintatulosServiceComponent =>

  val hakemusRepository: HakemusRepository

  class RemoteHakemusRepository extends HakemusRepository {
    import scala.collection.JavaConversions._
    private val dao = springContext.applicationDAO
    private val applicationService = springContext.applicationService
    private val applicationValidator: ApplicationValidator = newApplicationValidator

    private def canUpdate(lomake: Lomake, originalApplication: Application, updatedApplication: Application, userOid: String)(implicit lang: Language.Language): Boolean = {
      val stateUpdateable = originalApplication.getState == Application.State.ACTIVE || originalApplication.getState == Application.State.INCOMPLETE
      val inPostProcessing = !(originalApplication.getRedoPostProcess == Application.PostProcessingState.DONE || originalApplication.getRedoPostProcess() == null)
      (isActiveHakuPeriod(lomake) || hasOnlyContactInfoChangesAndApplicationRoundHasNotEnded(lomake, originalApplication, updatedApplication)) &&
      stateUpdateable &&
      !inPostProcessing &&
      userOid == originalApplication.getPersonOid
    }

    private def isActiveHakuPeriod(lomake: Lomake)(implicit lang: Language.Language) = {
      val applicationPeriods = lomakeRepository.applicationPeriodsByOid(lomake.oid)
      applicationPeriods.exists(_.active)
    }

    private def hasOnlyContactInfoChangesAndApplicationRoundHasNotEnded(lomake: Lomake, originalApplication: Application, updatedApplication: Application): Boolean = {
      val oldAnswers = originalApplication.getVastauksetMerged
      val newAnswers = updatedApplication.getVastauksetMerged
      val allKeys = oldAnswers.keySet() ++ newAnswers.keySet()
      new LocalDateTime().isBefore(ohjausparametritService.haunAikataulu(lomake.oid).flatMap(_.hakukierrosPaattyy).map(new LocalDateTime(_ : Long)).getOrElse(new LocalDateTime().plusYears(100))) && allKeys.filter(
        key => {
          val oldValue = oldAnswers.getOrElse(key, "")
          val newValue = newAnswers.getOrElse(key, "")
          if(oldValue.equals(newValue)) {
            false
          }
          else {
            !isContactInformationChange(key)
          }
        }
      ).isEmpty
    }

    private def isContactInformationChange(key: String): Boolean = {
      List(OppijaConstants.ELEMENT_ID_FIN_ADDRESS, OppijaConstants.ELEMENT_ID_EMAIL, OppijaConstants.ELEMENT_ID_FIN_POSTAL_NUMBER).contains(key) ||
      key.startsWith(OppijaConstants.ELEMENT_ID_PREFIX_PHONENUMBER)
    }

    override def updateHakemus(lomake: Lomake, haku: Haku)(hakemus: HakemusMuutos, userOid: String)(implicit lang: Language.Language): Option[Hakemus] = {
      val applicationQuery: Application = new Application().setOid(hakemus.oid)
      val applicationJavaObject: Option[Application] = timed(1000, "Application fetch DAO"){
        dao.find(applicationQuery).toList.headOption
      }

      timed(1000, "Application update"){
        applicationJavaObject.map(updateApplication(lomake, _, hakemus)).filter {
          case (originalApplication: Application, application: Application) => canUpdate(lomake, originalApplication, application, userOid)
        }.map {
          case (originalApplication, application) =>
          timed(1000, "Application update DAO"){
            dao.update(applicationQuery, application)
          }
          auditLogger.log(UpdateHakemus(userOid, hakemus.oid, haku.oid, originalApplication.getAnswers.toMap.mapValues(_.toMap), application.getAnswers.toMap.mapValues(_.toMap)))
          hakemusConverter.convertToHakemus(lomake, haku, application)
        }
      }
    }

    private def updateApplication(lomake: Lomake, application: Application, hakemus: HakemusMuutos)(implicit lang: Language.Language): (Application, Application) = {
      val originalApplication = application.clone()
      ApplicationUpdater.update(lomake, application, hakemus)
      timed(1000, "ApplicationService: update preference based data"){
        applicationService.updatePreferenceBasedData(application)
      }
      timed(1000, "ApplicationService: update authorization Meta"){
        applicationService.updateAuthorizationMeta(application)
      }
      (originalApplication, application)
    }

    override def findStoredApplicationByOid(oid: String): Application = {
      val applications = timed(1000, "Application fetch DAO"){
        dao.find(new Application().setOid(oid)).toList
      }
      if (applications.size > 1) throw new RuntimeException("Too many applications for oid " + oid)
      if (applications.size == 0) throw new RuntimeException("Application not found for oid " + oid)
      val application = applications.head
      application
    }

    override def fetchHakemukset(personOid: String)(implicit lang: Language.Language) = {
      fetchHakemukset(new Application().setPersonOid(personOid))
    }

    override def getHakemus(personOid: String, hakemusOid: String)(implicit lang: Language) = {
      fetchHakemukset(new Application().setPersonOid(personOid).setOid(hakemusOid)).headOption
    }

    private def fetchHakemukset(query: Application)(implicit lang: Language) = {
      timed(1000, "Application fetch"){
        val applicationJavaObjects: List[Application] = timed(1000, "Application fetch DAO"){
          dao.find(query).toList
        }
        applicationJavaObjects.filter{
          application => {
            !application.getState.equals(Application.State.PASSIVE)
          }
        }.map(application => {
          val (lomakeOption, hakuOption) = timed(1000, "LomakeRepository get lomake"){
            lomakeRepository.lomakeAndHakuByApplication(application)
          }
          for {
            haku <- hakuOption
            lomake <- lomakeOption
          } yield {
            val valintatulos = fetchValintatulos(application, haku)
            val hakemus = hakemusConverter.convertToHakemus(lomake, haku, application, valintatulos._1)
            auditLogger.log(ShowHakemus(application.getPersonOid, hakemus.oid, haku.oid))

            if (haku.applicationPeriods.exists(_.active)) {
              applicationValidator.validateAndFindQuestions(haku, lomake, withNoPreferenceSpesificAnswers(hakemus), application) match {
                case (app, errors, questions) => HakemusInfo(hakemusConverter.convertToHakemus(lomake, haku, app, valintatulos._1), errors, questions, valintatulos._2)
              }
            }
            else {
              HakemusInfo(hakemus, List(), List(), valintatulos._2)
            }
          }
        }).flatten.toList.sortBy[Long](_.hakemus.received).reverse
      }
    }

    private def fetchValintatulos(application: Application, haku: Haku) = {
      if (hakemusConverter.anyApplicationPeriodEnded(haku, application)) {
        Try(valintatulosService.getValintatulos(application.getOid, haku.oid)) match {
          case Success(t) => (t, true)
          case Failure(e) => (None, false)
        }
      } else {
        (None, true)
      }
    }

    private def withNoPreferenceSpesificAnswers(hakemus: Hakemus): HakemusLike = {
      hakemus.toHakemusMuutos.copy(answers = hakemus.answers.filterKeys(!_.equals(HakutoiveetConverter.hakutoiveetPhase)))
    }

    override def exists(personOid: String, hakuOid: String, hakemusOid: String) = {
      dao.find(new Application().setPersonOid(personOid).setOid(hakemusOid).setApplicationSystemId(hakuOid)).size() == 1
    }
  }

}
