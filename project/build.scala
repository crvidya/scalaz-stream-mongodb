


import sbt._
import sbt.Keys._
import com.typesafe.sbt._
import SbtGhPages._
import SbtSite._
import SiteKeys._
import SbtGit._
import GitKeys._

object build extends Build {

  lazy val specs2Version = "2.2.3"

  lazy val resolverSettings =
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases")
      , Resolver.sonatypeRepo("snapshots")
      , "Spinoco Nexus Releases" at "https://maven.spinoco.com/nexus/content/repositories/releases/"
      , "Spinoco Nexus Snapshots" at "https://maven.spinoco.com/nexus/content/repositories/snapshots/"
    )


  lazy val credentialsSettings = Seq(
    credentials += {
      Seq("build.publish.user", "build.publish.password").map(k => Option(System.getProperty(k))) match {
        case Seq(Some(user), Some(pass)) =>
          Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
        case _ =>
          Credentials(Path.userHome / ".ivy2" / ".credentials.sonatype")
      }
    }
  )

  lazy val libraries = Seq(
    libraryDependencies ++= Seq(
      "org.mongodb" % "mongo-java-driver" % "2.11.3"
      , "org.scalaz.stream" %% "scalaz-stream" % "0.1" exclude("org.scala-lang", "*")
    )
  )

  lazy val testLibraries =
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.10.1" % "test" exclude("org.scala-lang", "*")
      , "org.specs2" %% "specs2" % specs2Version % "test" exclude("org.scalaz", "*")
      , "org.pegdown" % "pegdown" % "1.2.1" % "test"
      , "junit" % "junit" % "4.7" % "test"
    )


  lazy val compileSettings = Seq(
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")
    , scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true
    , publishTo <<= (version).apply {
      v =>
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT"))
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
    , publishArtifact in Test := false
    , pomIncludeRepository := { _ => false }
    , pomExtra := (
        <url>http://spinoco.github.io/scalaz-stream-mongodb</url>
        <licenses>
          <license>
            <name>MIT</name>
            <url>https://github.com/Spinoco/scalaz-stream-mongodb/blob/master/LICENSE</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:Spinoco/scalaz-stream-mongodb.git</url>
          <connection>scm:git:git@github.com:Spinoco/scalaz-stream-mongodb.git</connection>
        </scm>
        <developers>
          <developer>
            <id>spinoco</id>
            <name>Spinoco</name>
            <url>http://www.spinoco.com</url>
          </developer>
        </developers>
      ))


  lazy val webSettings = SbtSite.site.settings ++ SbtSite.site.includeScaladoc()

  lazy val siteSettings = Seq(
    siteMappings <++= (mappings in packageDoc in Compile, version) map {
      (m, v) =>
        for ((f, d) <- m) yield {
          (f, if (v.trim.endsWith("SNAPSHOT")) ("/api/master/" + d) else ("/api/streams-mongodb-" + v + "/" + d))
        }
    }
  )


  lazy val buildSettings =
    Defaults.defaultSettings ++
      Seq(
        organization := "com.spinoco"
        , version := "0.3.0-SNAPSHOT"
        , scalaVersion := "2.10.2"
        , conflictManager := ConflictManager.strict
        , shellPrompt := ShellPrompt.buildShellPrompt
        , testOptions in Test += Tests.Argument("html", "console", "junitxml")
        , concurrentRestrictions in Global += Tags.limit(Tags.Test, java.lang.Runtime.getRuntime.availableProcessors() / 2 min 1 max 4) //restrict tests to max 4 threads
      ) ++
      resolverSettings ++
      credentialsSettings ++
      libraries ++
      testLibraries ++
      compileSettings ++
      webSettings ++
      publishSettings ++
      net.virtualvoid.sbt.graph.Plugin.graphSettings


  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //
  //       PROJECTS
  //
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  lazy val main = Project("scalaz-stream-mongodb-main", file("."), settings = buildSettings ++ ghpages.settings ++
    SbtSite.site.addMappingsToSiteDir(mappings in packageDoc in core in Compile, "/core") ++
    SbtSite.site.addMappingsToSiteDir(mappings in packageDoc in spec in Compile, "/spec") ++
    Seq(
      gitRemoteRepo := "git@github.com:Spinoco/scalaz-stream-mongodb.git"
      , siteMappings <++= target map ((p: File) => {
        val pfxSz = p.toPath.resolve("specs2-reports").toString.size
        val newPfx = "reports"
        (((p \ "specs2-reports") ** "*") x (f => Some(newPfx + f.toString.substring(pfxSz))))
      })
    )).aggregate(core, spec)

  lazy val core = Project("scalaz-stream-mongodb", file("core"), settings = buildSettings ++ siteSettings ++ Seq(
    previewSite <<= sourceDirectory map (_ => ())
  )).dependsOn(spec % "test")

  lazy val spec = Project("scalaz-stream-mongodb-spec", file("spec"), settings = buildSettings ++ siteSettings ++ Seq(
    libraryDependencies += "org.specs2" %% "specs2" % specs2Version exclude("org.scalaz", "*")
    , libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)
    , previewSite <<= sourceDirectory map (_ => ())
  ))

}

