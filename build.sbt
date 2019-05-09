lazy val `http4s-jdk-http-client` = project.in(file("."))
  .settings(commonSettings, releaseSettings, skipOnPublishSettings)
  .aggregate(core, docs)

lazy val core = project.in(file("core"))
  .settings(commonSettings, releaseSettings, mimaSettings)
  .settings(
    name := "http4s-jdk-http-client"
  )

lazy val docs = project.in(file("docs"))
  .enablePlugins(GhpagesPlugin, GitPlugin, MdocPlugin, ParadoxMaterialThemePlugin, ParadoxSitePlugin)
  .dependsOn(core)
  .settings(commonSettings, skipOnPublishSettings, docsSettings)

lazy val contributors = Seq(
  "ChristopherDavenport"  -> "Christopher Davenport",
  "rossabaker"            -> "Ross A. Baker",
)

val catsV = "1.6.0"
val catsEffectV = "1.3.0"
val fs2V = "1.0.4"
val http4sV = "0.20.0"
val reactiveStreamsV = "1.0.2"

val specs2V = "4.5.1"

val kindProjectorV = "0.10.0"
val betterMonadicForV = "0.3.0"

// General Settings
lazy val commonSettings = Seq(
  organization := "org.http4s",

  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value),
  scalacOptions += "-Yrangepos",

  git.remoteRepo := "git@github.com:http4s/http4s-jdk-http-client.git",

  scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/http4s/http4s-jdk-http-client/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),

  addCompilerPlugin("org.typelevel" % "kind-projector" % kindProjectorV cross CrossVersion.binary),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "org.typelevel"               %% "cats-core"                      % catsV,
    "org.typelevel"               %% "cats-effect"                    % catsEffectV,
    "co.fs2"                      %% "fs2-core"                       % fs2V,
    "co.fs2"                      %% "fs2-io"                         % fs2V,
    "co.fs2"                      %% "fs2-reactive-streams"           % fs2V,
    "org.http4s"                  %% "http4s-client"                  % http4sV,
    "org.reactivestreams"         %  "reactive-streams-flow-adapters" % reactiveStreamsV,
    
    "org.http4s"                  %% "http4s-testing"                 % http4sV       % Test,
    "org.specs2"                  %% "specs2-core"                    % specs2V       % Test,
    "org.specs2"                  %% "specs2-scalacheck"              % specs2V       % Test
  )
)

lazy val releaseSettings = {
  import ReleaseTransformations._
  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (
      for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield
        Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password
        )
    ).toSeq,
    publishArtifact in Test := false,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/http4s/http4s-jdk-http-client"),
        "git@github.com:http4s/http4s-jdk-http-client.git"
      )
    ),
    homepage := Some(url("https://github.com/http4s/http4s-jdk-http-client")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra := {
      <developers>
        {for ((username, name) <- contributors) yield
        <developer>
          <id>{username}</id>
          <name>{name}</name>
          <url>http://github.com/{username}</url>
        </developer>
        }
      </developers>
    }
  )
}

lazy val mimaSettings = {
  import sbtrelease.Version

  def semverBinCompatVersions(major: Int, minor: Int, patch: Int): Set[(Int, Int, Int)] = {
    val majorVersions: List[Int] =
      if (major == 0 && minor == 0) List.empty[Int] // If 0.0.x do not check MiMa
      else List(major)
    val minorVersions : List[Int] =
      if (major >= 1) Range(0, minor).inclusive.toList
      else List(minor)
    def patchVersions(currentMinVersion: Int): List[Int] = 
      if (minor == 0 && patch == 0) List.empty[Int]
      else if (currentMinVersion != minor) List(0)
      else Range(0, patch - 1).inclusive.toList

    val versions = for {
      maj <- majorVersions
      min <- minorVersions
      pat <- patchVersions(min)
    } yield (maj, min, pat)
    versions.toSet
  }

  def mimaVersions(version: String): Set[String] = {
    Version(version) match {
      case Some(Version(major, Seq(minor, patch), _)) =>
        semverBinCompatVersions(major.toInt, minor.toInt, patch.toInt)
          .map{case (maj, min, pat) => maj.toString + "." + min.toString + "." + pat.toString}
      case _ =>
        Set.empty[String]
    }
  }
  // Safety Net For Exclusions
  lazy val excludedVersions: Set[String] = Set()

  // Safety Net for Inclusions
  lazy val extraVersions: Set[String] = Set()

  Seq(
    mimaFailOnProblem := mimaVersions(version.value).toList.headOption.isDefined,
    mimaPreviousArtifacts := (mimaVersions(version.value) ++ extraVersions)
      .filterNot(excludedVersions.contains(_))
      .map{v => 
        val moduleN = moduleName.value + "_" + scalaBinaryVersion.value.toString
        organization.value % moduleN % v
      },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq()
    }
  )
}

lazy val docsSettings = {
  ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox) ++
  Seq(
    mdocIn := (baseDirectory.value) / "src" / "main" / "mdoc", // 
    mdocVariables := Map(
      "VERSION" -> version.value,
      "BINARY_VERSION" -> binaryVersion(version.value),
      "HTTP4S_VERSION" -> "0.20",
      "SCALA_VERSIONS" -> formatCrossScalaVersions(crossScalaVersions.value.toList)
    ),
    scalacOptions in mdoc --= Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused-import",

      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports",
      "-Xlint:-missing-interpolator,_"
    ),

    sourceDirectory in Paradox := mdocOut.value,
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    Paradox / paradoxMaterialTheme ~= { _
      .withRepository(uri("https://github.com/http4s/http4s-jdk-http-client"))
      .withLogoUri(uri("https://http4s.org/images/http4s-logo.svg"))
      .withCopyright(
        """
       |<a href="https://github.com/http4s/http4s-jdk-http-client/blob/master/LICENSE"><img src="https://img.shields.io/github/license/http4s/http4s-jdk-http-client.svg?color=darkgreen" alt="License: Apache 2.0" /></a>
       |<a href="https://typelevel.org/projects/"><img src="https://img.shields.io/badge/typelevel-incubator-teal.svg?logo=data:image/svg%2Bxml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8%2BPHN2ZyAgIHhtbG5zOnNrZXRjaD0iaHR0cDovL3d3dy5ib2hlbWlhbmNvZGluZy5jb20vc2tldGNoL25zIiAgIHhtbG5zOmRjPSJodHRwOi8vcHVybC5vcmcvZGMvZWxlbWVudHMvMS4xLyIgICB4bWxuczpjYz0iaHR0cDovL2NyZWF0aXZlY29tbW9ucy5vcmcvbnMjIiAgIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyIgICB4bWxuczpzdmc9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiAgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgICB4bWxuczpzb2RpcG9kaT0iaHR0cDovL3NvZGlwb2RpLnNvdXJjZWZvcmdlLm5ldC9EVEQvc29kaXBvZGktMC5kdGQiICAgeG1sbnM6aW5rc2NhcGU9Imh0dHA6Ly93d3cuaW5rc2NhcGUub3JnL25hbWVzcGFjZXMvaW5rc2NhcGUiICAgdmlld0JveD0iLTk5LjUgNzAuNSAxNC4wNzA2MTEgMTYuNjY0MjcyIiAgIGVuYWJsZS1iYWNrZ3JvdW5kPSJuZXcgLTk5LjUgNzAuNSAxNDEgNDciICAgaWQ9InN2ZzMzMzYiICAgdmVyc2lvbj0iMS4xIiAgIGlua3NjYXBlOnZlcnNpb249IjAuOTEgcjEzNzI1IiAgIHNvZGlwb2RpOmRvY25hbWU9ImxvZ28uc3ZnIiAgIHdpZHRoPSIxNC4wNzA2MTEiICAgaGVpZ2h0PSIxNi42NjQyNzIiPiAgPG1ldGFkYXRhICAgICBpZD0ibWV0YWRhdGEzMzY2Ij4gICAgPHJkZjpSREY%2BICAgICAgPGNjOldvcmsgICAgICAgICByZGY6YWJvdXQ9IiI%2BICAgICAgICA8ZGM6Zm9ybWF0PmltYWdlL3N2Zyt4bWw8L2RjOmZvcm1hdD4gICAgICAgIDxkYzp0eXBlICAgICAgICAgICByZGY6cmVzb3VyY2U9Imh0dHA6Ly9wdXJsLm9yZy9kYy9kY21pdHlwZS9TdGlsbEltYWdlIiAvPiAgICAgICAgPGRjOnRpdGxlPlBhZ2UtMV8xMl88L2RjOnRpdGxlPiAgICAgIDwvY2M6V29yaz4gICAgPC9yZGY6UkRGPiAgPC9tZXRhZGF0YT4gIDxkZWZzICAgICBpZD0iZGVmczMzNjQiPiAgICA8ZmlsdGVyICAgICAgIHN0eWxlPSJjb2xvci1pbnRlcnBvbGF0aW9uLWZpbHRlcnM6c1JHQiIgICAgICAgaW5rc2NhcGU6bGFiZWw9IkdyZXlzY2FsZSIgICAgICAgaWQ9ImZpbHRlcjMzNjgiPiAgICAgIDxmZUNvbG9yTWF0cml4ICAgICAgICAgdmFsdWVzPSIwLjIxIDAuNzIgMC4wNzIgMCAwIDAuMjEgMC43MiAwLjA3MiAwIDAgMC4yMSAwLjcyIDAuMDcyIDAgMCAwIDAgMCAxIDAgIiAgICAgICAgIGlkPSJmZUNvbG9yTWF0cml4MzM3MCIgICAgICAgICByZXN1bHQ9ImZiU291cmNlR3JhcGhpYyIgLz4gICAgICA8ZmVDb2xvck1hdHJpeCAgICAgICAgIHJlc3VsdD0iZmJTb3VyY2VHcmFwaGljQWxwaGEiICAgICAgICAgaW49ImZiU291cmNlR3JhcGhpYyIgICAgICAgICB2YWx1ZXM9IjAgMCAwIC0xIDAgMCAwIDAgLTEgMCAwIDAgMCAtMSAwIDAgMCAwIDEgMCIgICAgICAgICBpZD0iZmVDb2xvck1hdHJpeDMzNzIiIC8%2BICAgICAgPGZlQ29sb3JNYXRyaXggICAgICAgICBpZD0iZmVDb2xvck1hdHJpeDMzNzQiICAgICAgICAgdmFsdWVzPSIwLjIxIDAuNzIgMC4wNzIgMCAwIDAuMjEgMC43MiAwLjA3MiAwIDAgMC4yMSAwLjcyIDAuMDcyIDAgMCAwIDAgMCAxIDAgIiAgICAgICAgIGluPSJmYlNvdXJjZUdyYXBoaWMiIC8%2BICAgIDwvZmlsdGVyPiAgPC9kZWZzPiAgPHNvZGlwb2RpOm5hbWVkdmlldyAgICAgcGFnZWNvbG9yPSIjZmZmZmZmIiAgICAgYm9yZGVyY29sb3I9IiM2NjY2NjYiICAgICBib3JkZXJvcGFjaXR5PSIxIiAgICAgb2JqZWN0dG9sZXJhbmNlPSIxMCIgICAgIGdyaWR0b2xlcmFuY2U9IjEwIiAgICAgZ3VpZGV0b2xlcmFuY2U9IjEwIiAgICAgaW5rc2NhcGU6cGFnZW9wYWNpdHk9IjAiICAgICBpbmtzY2FwZTpwYWdlc2hhZG93PSIyIiAgICAgaW5rc2NhcGU6d2luZG93LXdpZHRoPSIxOTIwIiAgICAgaW5rc2NhcGU6d2luZG93LWhlaWdodD0iMTAxNyIgICAgIGlkPSJuYW1lZHZpZXczMzYyIiAgICAgc2hvd2dyaWQ9ImZhbHNlIiAgICAgaW5rc2NhcGU6em9vbT0iOC44NTEzNzIxIiAgICAgaW5rc2NhcGU6Y3g9IjczLjI5MDc5MyIgICAgIGlua3NjYXBlOmN5PSI2LjQwMDgwMTkiICAgICBpbmtzY2FwZTp3aW5kb3cteD0iLTgiICAgICBpbmtzY2FwZTp3aW5kb3cteT0iLTgiICAgICBpbmtzY2FwZTp3aW5kb3ctbWF4aW1pemVkPSIxIiAgICAgaW5rc2NhcGU6Y3VycmVudC1sYXllcj0ic3ZnMzMzNiIgICAgIGZpdC1tYXJnaW4tdG9wPSIwIiAgICAgZml0LW1hcmdpbi1sZWZ0PSIwIiAgICAgZml0LW1hcmdpbi1yaWdodD0iMCIgICAgIGZpdC1tYXJnaW4tYm90dG9tPSIwIiAvPiAgPHN0eWxlICAgICB0eXBlPSJ0ZXh0L2NzcyIgICAgIGlkPSJzdHlsZTMzMzgiPi5zdDB7ZmlsbDp1cmwoI1NoYXBlXzJfKTt9IC5zdDF7ZmlsbDojRkY2MTY5O30gLnN0MntmaWxsOiNGRkZGRkY7fSAuc3Qze2ZpbGw6I0ZGQjRCNTt9IC5zdDR7b3BhY2l0eTowLjk7fSAuc3Q1e2ZpbGw6IzIxMzAzRjt9PC9zdHlsZT4gIDx0aXRsZSAgICAgaWQ9InRpdGxlMzM0MCI%2BUGFnZS0xXzEyXzwvdGl0bGU%2BICA8ZGVzYyAgICAgaWQ9ImRlc2MzMzQyIj5DcmVhdGVkIHdpdGggU2tldGNoIEJldGEuPC9kZXNjPiAgPGcgICAgIGlkPSJQYWdlLTEiICAgICBza2V0Y2g6dHlwZT0iTVNQYWdlIiAgICAgdHJhbnNmb3JtPSJtYXRyaXgoMC41NDAzNDYwNCwwLDAsMC41NDAzNDYwNCwtNDQuNzc2OTk1LDMzLjY0NTY1NikiPiAgICA8bGluZWFyR3JhZGllbnQgICAgICAgaWQ9IlNoYXBlXzJfIiAgICAgICBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgICAgICAgeDE9Ii0xMTUuNTc3IiAgICAgICB5MT0iMi4yNDMiICAgICAgIHgyPSItMTE0LjU4NiIgICAgICAgeTI9IjEuNzM1IiAgICAgICBncmFkaWVudFRyYW5zZm9ybT0ibWF0cml4KDM5LjgsMCwwLDQ1LjgsNDUwMS42LDIuMykiPiAgICAgIDxzdG9wICAgICAgICAgb2Zmc2V0PSIwIiAgICAgICAgIHN0b3AtY29sb3I9IiNGRjRDNjEiICAgICAgICAgaWQ9InN0b3AzMzQ2IiAvPiAgICAgIDxzdG9wICAgICAgICAgb2Zmc2V0PSIxIiAgICAgICAgIHN0b3AtY29sb3I9IiNGNTFDMkIiICAgICAgICAgaWQ9InN0b3AzMzQ4IiAvPiAgICA8L2xpbmVhckdyYWRpZW50PiAgICA8ZyAgICAgICBpZD0iU2hhcGVfMV8iICAgICAgIHN0eWxlPSJmaWx0ZXI6dXJsKCNmaWx0ZXIzMzY4KSIgICAgICAgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTkuMjA0MDAwOSwtNy43MjQ5MjE0KSI%2BICAgICAgPHBhdGggICAgICAgICBjbGFzcz0ic3QxIiAgICAgICAgIGQ9Im0gLTg1LjIsOTguOSAwLDUuMyAxMi41LC03IDAsLTUuNCAtMTIuNSw3LjEgeiIgICAgICAgICBpZD0icGF0aDMzNTIiICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIgICAgICAgICBzdHlsZT0iZmlsbDojZmY2MTY5IiAvPiAgICAgIDxwYXRoICAgICAgICAgY2xhc3M9InN0MiIgICAgICAgICBkPSJtIC04NS4yLDg4LjIgLTQuNywtMi42IDAsMTYgNC42LDIuNiAwLC01LjMgNC42LDIuNyAwLjEsLTUuNCAtNC43LC0yLjYgMC4xLC01LjQiICAgICAgICAgaWQ9InBhdGgzMzU0IiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZiIgLz4gICAgICA8cGF0aCAgICAgICAgIGNsYXNzPSJzdDMiICAgICAgICAgZD0ibSAtODAuNiw5Ni4yIDAsNS40IDEyLjQsLTcuMSAwLC01LjMgLTEyLjQsNyB6IG0gNy44LC0xNSAtMTIuNSw3IDAsNS40IDEyLjUsLTcuMSAwLC01LjMgeiIgICAgICAgICBpZD0icGF0aDMzNTYiICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIgICAgICAgICBzdHlsZT0iZmlsbDojZmZiNGI1IiAvPiAgICAgIDxwYXRoICAgICAgICAgY2xhc3M9InN0MSIgICAgICAgICBkPSJtIC03Mi43LDg2LjUgLTEyLjUsNy4xIDQuNiwyLjYgMTIuNCwtNy4xIC00LjUsLTIuNiB6IG0gLTAuMSwtNS4zIC00LjYsLTIuNyAtMTIuNSw3LjEgNC42LDIuNiAxMi41LC03IHoiICAgICAgICAgaWQ9InBhdGgzMzU4IiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmNjE2OSIgLz4gICAgPC9nPiAgPC9nPjwvc3ZnPg==" alt="Typelevel incubator project" /></a>
       |<a href="https://http4s.org/code-of-conduct/"><img src="https://img.shields.io/badge/Code%20of%20Conduct-Scala-blue.svg" alt="Code of Conduct: Scala" /></a>
       |""".stripMargin)
      .withSocial(
        uri("https://github.com/http4s/http4s-jdk-http-client"),
        uri("https://twitter.com/http4s"),
      )
    },
    Paradox / paradoxProperties ++= Map(
      "include.build.base_dir" -> (baseDirectory in ThisBuild).value.getAbsolutePath,
    ),
    Paradox / siteSubdirName := docVersion(version.value),
    includeFilter in ghpagesCleanSite := new FileFilter {
      val Prefix = (ghpagesRepository.value / docVersion(version.value))
      def accept(f: File) = f match {
        case null => false
        case Prefix => true
        case _ => accept(f.getParentFile)
      }
    }
  )
}

lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)

def binaryVersion(version: String) =
  version match {
    case VersionNumber(Seq(0, minor, _*), _, _) => s"0.$minor"
    case VersionNumber(Seq(major, _, _*), _, _) if major > 0 => major.toString
  }

def formatCrossScalaVersions(crossScalaVersions: List[String]): String = {
  def go(vs: List[String]): String = {
    vs match {
      case Nil => ""
      case a :: Nil => a
      case a :: b :: Nil => s"$a and $b"
      case a :: bs => s"$a, ${go(bs)}"
    }
  }
  go(crossScalaVersions.map(CrossVersion.binaryScalaVersion))
}

def docVersion(version: String) = 
  version match {
    case VersionNumber(_, Seq(tags), _) if tags.contains("SNAPSHOT") => "latest"
    case _ => version
  }

