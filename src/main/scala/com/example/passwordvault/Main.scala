package com.example.passwordvault

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s.*
import com.example.passwordvault.auth.{PasswordHasher, TokenService}
import com.example.passwordvault.config.AppConfig
import com.example.passwordvault.crypto.Crypto
import com.example.passwordvault.db.Database
import com.example.passwordvault.http.ApiRoutes
import com.example.passwordvault.repository.{PasswordRepository, UserRepository}
import com.example.passwordvault.service.{AuthService, PasswordService}
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    AppConfig.load.flatMap { config =>
      val appResource =
        for {
          crypto <- Resource.eval(Crypto.aesGcm(config.masterKeyBase64))
          port <- Resource.eval(
            IO.fromOption(Port.fromInt(config.httpPort))(
              new IllegalArgumentException(s"Invalid HTTP_PORT: ${config.httpPort}")
            )
          )

          _          <- Resource.eval(Database.migrate(config.database))
          transactor <- Database.transactor(config.database)

          authService = AuthService.live(
            users = UserRepository.postgres[IO](transactor),
            hasher = PasswordHasher.bcrypt(),
            tokens = TokenService.jwt(config.auth.jwtSecret, config.auth.tokenTtlSeconds)
          )

          passwordService = PasswordService.live(
            repository = PasswordRepository.postgres[IO](transactor),
            crypto = crypto
          )

          httpApp = new ApiRoutes(passwordService, authService).routes.orNotFound

          server <- EmberServerBuilder
            .default[IO]
            .withHost(host"0.0.0.0")
            .withPort(port)
            .withHttpApp(httpApp)
            .build
        } yield server

      appResource.useForever
    }
}
