package com.example.passwordvault.config

import cats.effect.IO

final case class DatabaseConfig(
    url: String,
    user: String,
    password: String,
    poolSize: Int
)

final case class AuthConfig(
    jwtSecret: String,
    tokenTtlSeconds: Long
)

final case class AppConfig(
    httpPort: Int,
    database: DatabaseConfig,
    masterKeyBase64: String,
    auth: AuthConfig
)

object AppConfig {

  def load: IO[AppConfig] =
    for {
      httpPort        <- intEnv("HTTP_PORT", 8080)
      dbUrl           <- requiredEnv("DB_URL")
      dbUser          <- requiredEnv("DB_USER")
      dbPassword      <- requiredEnv("DB_PASSWORD")
      dbPoolSize      <- intEnv("DB_POOL_SIZE", 10)
      masterKeyBase64 <- requiredEnv("MASTER_KEY_BASE64")
      jwtSecret       <- requiredEnv("JWT_SECRET")
      _ <- IO.raiseWhen(jwtSecret.length < 32)(
        new IllegalArgumentException("JWT_SECRET must be at least 32 characters")
      )
      tokenTtl <- intEnv("JWT_TTL_SECONDS", 3600)
    } yield AppConfig(
      httpPort = httpPort,
      database = DatabaseConfig(
        url = dbUrl,
        user = dbUser,
        password = dbPassword,
        poolSize = dbPoolSize
      ),
      masterKeyBase64 = masterKeyBase64,
      auth = AuthConfig(
        jwtSecret = jwtSecret,
        tokenTtlSeconds = tokenTtl.toLong
      )
    )

  private def requiredEnv(name: String): IO[String] =
    IO.fromOption(sys.env.get(name).filter(_.nonEmpty))(
      new IllegalArgumentException(s"Missing required environment variable: $name")
    )

  private def intEnv(name: String, default: Int): IO[Int] =
    sys.env.get(name) match {
      case None => IO.pure(default)
      case Some(value) =>
        IO.fromOption(value.toIntOption)(
          new IllegalArgumentException(s"Environment variable $name must be a number, got '$value'")
        )
    }
}
