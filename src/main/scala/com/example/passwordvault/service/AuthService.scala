package com.example.passwordvault.service

import cats.effect.IO
import com.example.passwordvault.auth.{PasswordHasher, TokenService}
import com.example.passwordvault.domain.*
import com.example.passwordvault.domain.Validation.orRaise
import com.example.passwordvault.repository.UserRepository

trait AuthService {
  def register(request: RegisterRequest): IO[UserView]
  def login(request: LoginRequest): IO[TokenResponse]

  def authenticate(token: String): IO[Long]
}

object AuthService {

  def live(
      users: UserRepository[IO],
      hasher: PasswordHasher,
      tokens: TokenService
  ): AuthService =
    new AuthService {

      override def register(request: RegisterRequest): IO[UserView] =
        for {
          valid   <- IO.fromEither(Validation.register(request).orRaise)
          hash    <- hasher.hash(valid.password)
          now     <- IO.realTime.map(_.toSeconds)
          created <- users.create(valid.login, hash, now)
          user <- IO.fromOption(created)(
            AppError.Conflict(s"User with login '${valid.login}' already exists")
          )
        } yield UserView(user.id, user.login)

      override def login(request: LoginRequest): IO[TokenResponse] = {
        val invalidCredentials = AppError.Unauthorized("Invalid login or password")

        for {
          maybeUser <- users.findByLogin(request.login)
          user      <- IO.fromOption(maybeUser)(invalidCredentials)
          ok        <- hasher.verify(request.password, user.passwordHash)
          _         <- IO.raiseUnless(ok)(invalidCredentials)
          token     <- tokens.issue(user.id)
        } yield TokenResponse(token, "Bearer", tokens.ttlSeconds)
      }

      override def authenticate(token: String): IO[Long] =
        tokens.verify(token).flatMap {
          case Some(userId) => IO.pure(userId)
          case None         => IO.raiseError(AppError.Unauthorized("Invalid or expired token"))
        }
    }
}
