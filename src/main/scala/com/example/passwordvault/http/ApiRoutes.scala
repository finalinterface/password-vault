package com.example.passwordvault.http

import cats.effect.IO
import com.example.passwordvault.domain.{AppError, ErrorResponse}
import com.example.passwordvault.http.Endpoints.ApiError
import com.example.passwordvault.service.{AuthService, PasswordService}
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.swagger.bundle.SwaggerInterpreter

final class ApiRoutes(passwords: PasswordService, auth: AuthService) {

  private def handle[A](io: IO[A]): IO[Either[ApiError, A]] =
    io.map(Right(_)).recover { case e: AppError => Left(ApiRoutes.toApiError(e)) }

  private val authenticate: String => IO[Either[ApiError, Long]] =
    token => handle(auth.authenticate(token))

  val serverEndpoints: List[ServerEndpoint[Any, IO]] = List(
    Endpoints.health.serverLogicSuccess(_ => IO.pure("OK")),
    Endpoints.register.serverLogic(request => handle(auth.register(request))),
    Endpoints.login.serverLogic(request => handle(auth.login(request))),
    Endpoints.listPasswords
      .serverSecurityLogic(authenticate)
      .serverLogic { userId => (search, exact, limit, offset) =>
        handle(passwords.list(userId, search, exact, limit, offset))
      },
    Endpoints.createPassword
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => request => handle(passwords.create(userId, request))),
    Endpoints.exportCsv
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => _ => handle(passwords.exportCsv(userId))),
    Endpoints.importCsv
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => csv => handle(passwords.importCsv(userId, csv))),
    Endpoints.getPassword
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => id => handle(passwords.get(userId, id))),
    Endpoints.getSecret
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => id => handle(passwords.secret(userId, id))),
    Endpoints.updatePassword
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => (id, request) => handle(passwords.update(userId, id, request))),
    Endpoints.deletePassword
      .serverSecurityLogic(authenticate)
      .serverLogic(userId => id => handle(passwords.delete(userId, id)))
  )

  private val docs: List[ServerEndpoint[Any, IO]] =
    SwaggerInterpreter().fromServerEndpoints[IO](serverEndpoints, "Password Vault API", "0.1.0")

  private val serverOptions: Http4sServerOptions[IO] =
    Http4sServerOptions
      .customiseInterceptors[IO]
      .defaultHandlers(message => ValuedEndpointOutput(jsonBody[ErrorResponse], ErrorResponse(message)))
      .options

  val routes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](serverOptions).toRoutes(serverEndpoints ++ docs)
}

object ApiRoutes {

  def toApiError(error: AppError): ApiError =
    error match {
      case AppError.NotFound(_) =>
        (StatusCode.NotFound, ErrorResponse(error.message))
      case AppError.ValidationFailed(errors) =>
        (StatusCode.BadRequest, ErrorResponse(error.message, errors))
      case AppError.Unauthorized(_) =>
        (StatusCode.Unauthorized, ErrorResponse(error.message))
      case AppError.Conflict(_) =>
        (StatusCode.Conflict, ErrorResponse(error.message))
    }
}
