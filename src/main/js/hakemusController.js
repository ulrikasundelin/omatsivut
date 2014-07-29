var Hakemus = require('./hakemus')

module.exports = function(listApp) {
  listApp.controller("hakemusController", ["$scope", "$element", "$http", "applicationsResource", "applicationValidator", "settings", "debounce", "localization", function ($scope, $element, $http, applicationsResource, applicationValidator, settings, debounce, localization) {
    applicationValidator = debounce(applicationValidator(), settings.modelDebounce)

    $scope.hasChanged = false
    $scope.isSaveable = true

    $scope.timestampLabel = function() {
      if ($scope.application.received == $scope.application.updated)
        return localization("timestamp_applicationReceived")
      else
        return localization("timestamp_applicationUpdated")
    }

    $scope.$watch("application.getHakutoiveWatchCollection()", function(hakutoiveet, oldHakutoiveet) {
      // Skip initial values angular style
      if (!_.isEqual(hakutoiveet, oldHakutoiveet)) {
        applicationChanged()
        validateHakutoiveet()
      }
    }, true)

    $scope.$watch("application.getAnswerWatchCollection()", function(answers, oldAnswers) {
      if (!_.isEqual(oldAnswers, [])) {
        applicationChanged()
      }
    }, true)

    function applicationChanged() {
      $scope.hasChanged = true
      if ($scope.statusMessageType == "success")
        setStatusMessage("")
    }

    function validateHakutoiveet() {
      setStatusMessage("", "pending")
      applicationValidator($scope.application, success, error)
      $scope.isSaveable = false

      function success(data) {
        $scope.isSaveable = true
        setStatusMessage("")
        $scope.application.importQuestions(data.questions)
        updateValidationMessages([], true)
      }

      function error(data) {
        var errorText

        if (!data.statusCode)
          errorText = localization("validationFailed")
        else
          errorText = (status == 401) ? localization("sessionExpired") : localization("validationFailed_httpError")

        $scope.isSaveable = data.isSaveable
        setStatusMessage(errorText, "error")
        if (data.questions)
          $scope.application.importQuestions(data.questions)
        updateValidationMessages(data.errors, true)
      }
    }

    function setStatusMessage(msg, type) {
      $scope.statusMessage = msg
      $scope.statusMessageType = type || ""
    }

    $scope.movePreference = function(from, to) {
      if (to >= 0 && to < this.application.hakutoiveet.length) {
        this.application.moveHakutoive(from, to)
        setStatusMessage()
      }
    }

    $scope.saveApplication = function() {
      applicationsResource.update({id: $scope.application.oid }, $scope.application.toJson(), onSuccess, onError)
      setStatusMessage("", "pending")

      function onSuccess(savedApplication) {
        $scope.$emit("highlight-save", $scope.application.getChangedItems())
        $scope.application.setAsSaved(savedApplication)
        $scope.hasChanged = false
        setStatusMessage(localization("changesSaved"), "success")
        updateValidationMessages([])
      }

      function onError(err) {
        var saveError = (function() { switch (err.status) {
          case 400:
            if (_.isArray(err.data) && err.data.length > 0)
              return "saveFailed_validationError"
            else
              return "serverError"
          case 401: return "saveFailed_sessionExpired"
          case 500: return "serverError"
          default: return "saveFailed"
        }})()

        setStatusMessage(localization(saveError), "error")
        if (err.status == 400) // Validointivirhe
          updateValidationMessages(err.data)
      }
    }

    function updateValidationMessages(errors, skipQuestions) {
      var unhandledMessages = $scope.application.updateValidationMessages(errors, skipQuestions)
      if (unhandledMessages.length > 0) {
        _(unhandledMessages).each(function(item) {
          console.log("Validaatiovirhettä ei käsitelty:", item.questionId, item.errors)
        })

        setStatusMessage(localization("serverError"), "error")
      }
    }
  }])
}