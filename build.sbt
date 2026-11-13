ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "com.example"

lazy val catsEffectVersion = "3.7.0"
lazy val http4sVersion     = "0.23.34"
lazy val circeVersion      = "0.14.15"
lazy val doobieVersion     = "1.0.0-RC12"
lazy val tapirVersion      = "1.11.7"
lazy val flywayVersion     = "11.20.3"
lazy val fs2DataVersion    = "1.11.2"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "password-vault-scala",
    libraryDependencies ++= Seq(
      // Effect runtime
      "org.typelevel" %% "cats-effect" % catsEffectVersion,

      // HTTP server
      "org.http4s" %% "http4s-ember-server" % http4sVersion,

      // Tapir & OpenAPI
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,

      // JSON
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,

      // Database
      "org.tpolecat" %% "doobie-core"     % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % doobieVersion,

      // Migrations
      "org.flywaydb" % "flyway-core"                % flywayVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,

      // CSV
      "org.gnieh" %% "fs2-data-csv" % fs2DataVersion,

      // Auth
      "at.favre.lib"          % "bcrypt"    % "0.10.2",
      "com.github.jwt-scala" %% "jwt-circe" % "10.0.1",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.5.33",

      // Tests
      "org.scalameta"     %% "munit"             % "1.0.0"  % Test,
      "org.typelevel"     %% "munit-cats-effect" % "2.0.0"  % Test,
      "org.testcontainers" % "postgresql"        % "1.21.4" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:imports"
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Djava.net.useSystemProxies=false",
      "-Dhttp.proxyHost=",
      "-Dhttps.proxyHost=",
      "-DsocksProxyHost="
    ),
    Test / fork := true
  )
