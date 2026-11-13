package com.example.passwordvault.http

import cats.effect.IO
import com.example.passwordvault.auth.{PasswordHasher, TokenService}
import com.example.passwordvault.domain.*
import com.example.passwordvault.service.{AuthService, PasswordService}
import com.example.passwordvault.testutil.{InMemoryPasswordRepository, InMemoryUserRepository, TestKeys}
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.headers.{`Content-Type`, Authorization}
import org.http4s.implicits.*

class ApiRoutesSuite extends CatsEffectSuite {

  private def makeApp: IO[HttpApp[IO]] =
    for {
      users     <- InMemoryUserRepository.make
      passwords <- InMemoryPasswordRepository.make
      crypto    <- TestKeys.crypto
      authService = AuthService.live(
        users,
        PasswordHasher.bcrypt(cost = 4),
        TokenService.jwt("test-secret-test-secret-test-secret!", 3600)
      )
      passwordService = PasswordService.live(passwords, crypto)
    } yield new ApiRoutes(passwordService, authService).routes.orNotFound

  private def jsonRequest[A: Encoder](method: Method, uri: Uri, body: A, token: Option[String] = None) =
    withToken(
      Request[IO](method, uri)
        .withEntity(body.asJson.noSpaces)
        .withContentType(`Content-Type`(MediaType.application.json)),
      token
    )

  private def withToken(request: Request[IO], token: Option[String]): Request[IO] =
    token.fold(request)(t => request.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, t))))

  private def bodyAs[A: Decoder](response: Response[IO]): IO[A] =
    response.as[String].flatMap(body => IO.fromEither(decode[A](body)))

  private def registerAndLogin(app: HttpApp[IO], login: String): IO[String] =
    for {
      _ <- app.run(jsonRequest(Method.POST, uri"/auth/register", RegisterRequest(login, "password123")))
      response <- app.run(jsonRequest(Method.POST, uri"/auth/login", LoginRequest(login, "password123")))
      token    <- bodyAs[TokenResponse](response)
    } yield token.accessToken

  test("health check works without token") {
    makeApp.flatMap(_.run(Request[IO](Method.GET, uri"/health"))).map { response =>
      assertEquals(response.status, Status.Ok)
    }
  }

  test("full flow: register, login, create, list without passwords, secret") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      createResp <- app.run(
        jsonRequest(
          Method.POST,
          uri"/passwords",
          CreatePasswordRequest("GitHub", "s3cret", None),
          Some(token)
        )
      )
      created  <- bodyAs[PasswordView](createResp)
      listResp <- app.run(withToken(Request[IO](Method.GET, uri"/passwords"), Some(token)))
      listBody <- listResp.as[String]
      secretResp <- app.run(
        withToken(
          Request[IO](Method.GET, Uri.unsafeFromString(s"/passwords/${created.id}/secret")),
          Some(token)
        )
      )
      secret <- bodyAs[PasswordSecret](secretResp)
    } yield {
      assertEquals(createResp.status, Status.Created)
      assertEquals(listResp.status, Status.Ok)
      assert(!listBody.contains("s3cret"), "list must not contain passwords")
      assert(listBody.contains("GitHub"))
      assertEquals(secret.password, "s3cret")
    }
  }

  test("without token -> 401") {
    makeApp.flatMap(_.run(Request[IO](Method.GET, uri"/passwords"))).map { response =>
      assertEquals(response.status, Status.Unauthorized)
    }
  }

  test("with invalid token -> 401") {
    makeApp
      .flatMap(_.run(withToken(Request[IO](Method.GET, uri"/passwords"), Some("garbage"))))
      .map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("other user's entry -> 404") {
    for {
      app        <- makeApp
      aliceToken <- registerAndLogin(app, "alice")
      bobToken   <- registerAndLogin(app, "bob")
      createResp <- app.run(
        jsonRequest(
          Method.POST,
          uri"/passwords",
          CreatePasswordRequest("GitHub", "s3cret", None),
          Some(aliceToken)
        )
      )
      created <- bodyAs[PasswordView](createResp)
      bobResp <- app.run(
        withToken(
          Request[IO](Method.GET, Uri.unsafeFromString(s"/passwords/${created.id}/secret")),
          Some(bobToken)
        )
      )
    } yield assertEquals(bobResp.status, Status.NotFound)
  }

  test("invalid 'exact' value -> 400, not the full list") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      response <- app.run(
        withToken(Request[IO](Method.GET, uri"/passwords?search=git&exact=yes"), Some(token))
      )
      body <- bodyAs[ErrorResponse](response)
    } yield {
      assertEquals(response.status, Status.BadRequest)
      assert(body.error.contains("exact"), body.error)
    }
  }

  test("validation error -> 400 with details") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      response <- app.run(
        jsonRequest(Method.POST, uri"/passwords", CreatePasswordRequest(" ", "", None), Some(token))
      )
      body <- bodyAs[ErrorResponse](response)
    } yield {
      assertEquals(response.status, Status.BadRequest)
      assertEquals(body.details.size, 2)
    }
  }

  test("malformed JSON -> 400 in JSON format") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      request = withToken(
        Request[IO](Method.POST, uri"/passwords")
          .withEntity("{not json")
          .withContentType(`Content-Type`(MediaType.application.json)),
        Some(token)
      )
      response <- app.run(request)
      body     <- bodyAs[ErrorResponse](response)
    } yield {
      assertEquals(response.status, Status.BadRequest)
      assert(body.error.nonEmpty)
    }
  }

  test("duplicate registration -> 409") {
    for {
      app <- makeApp
      _   <- app.run(jsonRequest(Method.POST, uri"/auth/register", RegisterRequest("alice", "password123")))
      second <- app.run(
        jsonRequest(Method.POST, uri"/auth/register", RegisterRequest("alice", "password123"))
      )
    } yield assertEquals(second.status, Status.Conflict)
  }

  test("export returns CSV attachment") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      _ <- app.run(
        jsonRequest(Method.POST, uri"/passwords", CreatePasswordRequest("GitHub", "a,b", None), Some(token))
      )
      response <- app.run(withToken(Request[IO](Method.GET, uri"/passwords/export"), Some(token)))
      body     <- response.as[String]
    } yield {
      assertEquals(response.status, Status.Ok)
      assertEquals(response.contentType.map(_.mediaType), Some(MediaType.text.csv))
      assert(response.headers.get(org.typelevel.ci.CIString("Content-Disposition")).isDefined)
      assert(body.contains("\"a,b\""), body)
    }
  }

  test("delete -> 204, then 404") {
    for {
      app   <- makeApp
      token <- registerAndLogin(app, "alice")
      createResp <- app.run(
        jsonRequest(Method.POST, uri"/passwords", CreatePasswordRequest("GitHub", "x", None), Some(token))
      )
      created <- bodyAs[PasswordView](createResp)
      uri = Uri.unsafeFromString(s"/passwords/${created.id}")
      first  <- app.run(withToken(Request[IO](Method.DELETE, uri), Some(token)))
      second <- app.run(withToken(Request[IO](Method.DELETE, uri), Some(token)))
    } yield {
      assertEquals(first.status, Status.NoContent)
      assertEquals(second.status, Status.NotFound)
    }
  }
}
