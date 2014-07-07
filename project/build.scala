import sbt._
import Keys._
import sbtbuildinfo.Plugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import com.earldouglas.xsbtwebplugin.WebPlugin
import com.earldouglas.xsbtwebplugin.WebPlugin.container
import com.earldouglas.xsbtwebplugin.PluginKeys._

object OmatsivutBuild extends Build {
  val Organization = "fi.vm.sade"
  val Name = "omatsivut"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.1"
  val ScalatraVersion = "2.3.0.RC3"
  val TomcatVersion = "7.0.22"

  // task for running mocha tests
  lazy val mocha = taskKey[Int]("run phantomJS tests")

  val mochaTask = mocha <<= (start in container.Configuration) map {
    Unit => {
      val pb = Seq("node_modules/mocha-phantomjs/bin/mocha-phantomjs" ,"-R", "spec", "http://localhost:8080/omatsivut/test/runner.html")
      val res = pb.!
      if(res != 0){
        sys.error("mocha tests failed")
      }
      res
    }
  }

  lazy val project = Project (
    "omatsivut",
    file("."),
    settings = Defaults.coreDefaultSettings ++ WebPlugin.webSettings ++ buildInfoSettings ++ mochaTask
      ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
      scalacOptions ++= Seq("-target:jvm-1.7", "-deprecation"),
      resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      resolvers += Classpaths.typesafeReleases,
      resolvers += "oph-sade-artifactory-releases" at "http://penaali.hard.ware.fi/artifactory/oph-sade-release-local",
      resolvers += "oph-sade-artifactory-snapshots" at "http://penaali.hard.ware.fi/artifactory/oph-sade-snapshot-local",
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Managed,
      buildInfoPackage := "fi.vm.sade.omatsivut",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-swagger" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "junit" % "junit" % "4.11" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
        "org.scalaj" %% "scalaj-http" % "0.3.15",
        "org.apache.tomcat.embed" % "tomcat-embed-core"         % TomcatVersion % "container;test",
        "org.apache.tomcat.embed" % "tomcat-embed-logging-juli" % TomcatVersion % "container;test",
        "org.apache.tomcat.embed" % "tomcat-embed-jasper"       % TomcatVersion % "container;test",
        "org.mongodb" %% "casbah" % "2.7.2",
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "org.json4s" %% "json4s-ext" % "3.2.10",
        "com.typesafe" % "config" % "1.2.1",
        "com.novus" %% "salat-core" % "1.9.8",
        "commons-codec" % "commons-codec" % "1.9",
        "fi.vm.sade.haku" % "hakemus-api" % "9.5-SNAPSHOT" excludeAll(
          ExclusionRule(organization = "org.json4s"),
          ExclusionRule(organization = "com.wordnik")
        ),
        "fi.vm.sade.haku" % "hakemus-api" % "9.5-SNAPSHOT" % "test" classifier "tests",
        "com.sun.jersey" % "jersey-client" % "1.17.1" // <- TODO: should be removed. Just patch for transitive dependency problem
        ,"org.springframework" % "spring-core" % "3.2.9.RELEASE" // <- try to force new spring
        ,"org.springframework" % "spring-jms" % "3.2.9.RELEASE"
        ,"org.springframework" % "spring-beans" % "3.2.9.RELEASE"
        ,"org.springframework" % "spring-context" % "3.2.9.RELEASE"
        ,"org.springframework" % "spring-aop" % "3.2.9.RELEASE"
        ,"org.springframework" % "spring-expression" % "3.2.9.RELEASE"
      ),
      artifactName <<= (name in (Compile, packageWar)) { projectName =>
        (config: ScalaVersion, module: ModuleID, artifact: Artifact) =>
          var newName = projectName
          if (module.revision.nonEmpty) {
            newName += "-" + module.revision
          }
          newName + "." + artifact.extension
      },
      artifactPath in (Compile, packageWar) ~= { defaultPath =>
        file("target") / defaultPath.getName
      },
      testOptions in Test += Tests.Argument("junitxml")
    ) ++ container.deploy(
      "/omatsivut" -> projectRef
    )
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
  lazy val projectRef: ProjectReference = project

}
