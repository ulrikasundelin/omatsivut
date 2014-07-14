(function () {
  describe('hakemuslistaus', function () {
    var page = ApplicationListPage();

    before(function (done) {
      session.init("010101-123N").then(page.resetDataAndOpen).done(done)
    })

    describe("näyttäminen", function() {
      it('hakemuslistassa on hakemus henkilölle 010101-123N', function () {
        expect(ApplicationListPage().applications()).to.deep.equal([
          {
            applicationSystemName: "Perusopetuksen jälkeisen valmistavan koulutuksen kesän 2014 haku MUOKATTU"
          }
        ])
      })

      it("henkilön 010101-123N hakutoiveet ovat näkyvissä", function () {
        expect(preferencesAsText()).to.deep.equal([
          {
            "hakutoive.Opetuspiste": "Amiedu, Valimotie 8",
            "hakutoive.Koulutus": "Maahanmuuttajien ammatilliseen peruskoulutukseen valmistava koulutus"
          },
          {
            "hakutoive.Opetuspiste": "Ammatti-instituutti Iisakki",
            "hakutoive.Koulutus": "Kymppiluokka"
          },
          {
            "hakutoive.Opetuspiste": "Turun Kristillinen opisto",
            "hakutoive.Koulutus": "Kymppiluokka"
          }
        ]);
      })
    })

    describe("validaatiot", function() {
      beforeEach(function() {
        return page.openPage()
          .then(leaveOnlyOnePreference)
      })

      function leaveOnlyOnePreference() {
        return ApplicationListPage().getPreference(0).remove()
          .then(function() { return ApplicationListPage().getPreference(0).remove() })
      }

      it("vain yksi hakutoive voi olla muokattavana kerrallaan", function() {
        var pref = page.getPreference(1)
        var pref2 = page.getPreference(2)
        return pref.selectOpetusPiste("Ahl")()
          .then(function() { pref2.isEditable().should.be.false } )
          .then(pref.selectKoulutus(0))
          .then(function() { pref2.isEditable().should.be.true } )
      })

      it("tyhjiä rivejä ei voi muokata", function () {
        _.each(ApplicationListPage().emptyPreferencesForApplication(0), function (row) {
          row.isDisabled().should.be.true
        })
      })

      it("ainoaa hakutoivetta ei voi poistaa", function() {
        ApplicationListPage().getPreference(0).canRemove().should.be.false
      })

      it("lomaketta ei voi tallentaa, jos hakutoive on epätäydellinen", function() {
        var pref = page.getPreference(1)
        return pref.selectOpetusPiste("Ahl")()
          .then(wait.until(page.saveButton(0).isEnabled(false)))
          .then(pref.selectKoulutus(0))
          .then(wait.until(page.saveButton(0).isEnabled(true)))
      })
    })

    describe("hakemuslistauksen muokkaus", function () {
      endToEndTest("järjestys", "järjestys muuttuu nuolta klikkaamalla", function () {
        return page.getPreference(1).moveDown()
      }, function (dbStart, dbEnd) {
        dbStart.hakutoiveet[1].should.deep.equal(dbEnd.hakutoiveet[2])
        dbStart.hakutoiveet[2].should.deep.equal(dbEnd.hakutoiveet[1])
      })
      endToEndTest("poisto", "hakutoiveen voi poistaa", function () {
        return page.getPreference(0).remove()
      }, function (dbStart, dbEnd) {
        dbEnd.hakutoiveet.should.deep.equal(_.flatten([_.rest(dbStart.hakutoiveet), {}]))
      })
      endToEndTest("lisäys", "hakutoiveen voi lisätä", function() {
        var pref = page.getPreference(2)
        return pref.remove()
          .then(pref.selectOpetusPiste("Ahl"))
          .then(pref.selectKoulutus(0)
        )
      }, function(dbStart, dbEnd) {
        var newOne = { 'Opetuspiste-id': '1.2.246.562.10.60222091211',
          Opetuspiste: 'Ahlmanin ammattiopisto',
          Koulutus: 'Ammattistartti',
          'Koulutus-id-kaksoistutkinto': 'false',
          'Koulutus-id-sora': 'false',
          'Koulutus-id-vocational': 'true',
          'Koulutus-id-lang': '',
          'Koulutus-id-aoIdentifier': '028',
          'Koulutus-id-athlete': 'false',
          'Koulutus-educationDegree': '32',
          'Koulutus-id': '1.2.246.562.14.2014040912353139913320',
          'Opetuspiste-id-parents': '',
          'Koulutus-id-educationcode': 'koulutus_039996' }
        dbEnd.hakutoiveet.should.deep.equal(dbStart.hakutoiveet.slice(0, 2).concat(newOne))
      })
    })
  })

  function endToEndTest(descName, testName, manipulationFunction, dbCheckFunction) {
    describe(descName, function() {
      var applicationsBefore, applicationsAfter;
      before(ApplicationListPage().resetDataAndOpen)
      before(function() {
        return db.getApplications().then(function(apps) {
          applicationsBefore = apps
        })
      })
      before(manipulationFunction)
      before(save)
      before(function(done) {
        db.getApplications().then(function(apps) {
          applicationsAfter = apps
          done()
        })
      })
      it(testName, function() {
        dbCheckFunction(applicationsBefore[0], applicationsAfter[0])
      })
    })

    function save() {
      return wait.until(ApplicationListPage().saveButton(0).isEnabled(true))()
        .then(ApplicationListPage().saveButton(0).click)
        .then(wait.until(ApplicationListPage().saveButton(0).isEnabled(false))) // Tallennus on joko alkanut tai valmis
        .then(wait.until(ApplicationListPage().isSavingState(0, false))) // Tallennus ei ole kesken
    }
  }

  function preferencesAsText() {
    return ApplicationListPage().preferencesForApplication(0).map(function (item) {
      return item.data()
    })
  }
})()