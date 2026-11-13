package com.example.passwordvault.auth

import cats.effect.IO
import com.example.passwordvault.domain.*
import com.example.passwordvault.service.AuthService
import com.example.passwordvault.testutil.InMemoryUserRepository
import munit.CatsEffectSuite

import java.time.{Clock, Instant, ZoneOffset}

class TokenServiceSuite extends CatsEffectSuite {

  private val secret = "test-secret-test-secret-test-secret!"

  test("issued token is verified and contains user id") {
    val tokens = TokenService.jwt(secret, 3600)
    tokens.issue(42).flatMap(tokens.verify).map(result => assertEquals(result, Some(42L)))
  }

  test("token signed with another secret is rejected") {
    val ours   = TokenService.jwt(secret, 3600)
    val theirs = TokenService.jwt("another-secret-another-secret-12345", 3600)
    theirs.issue(42).flatMap(ours.verify).map(result => assertEquals(result, None))
  }

  test("expired token is rejected") {
    val past   = Clock.fixed(Instant.now().minusSeconds(7200), ZoneOffset.UTC)
    val issuer = TokenService.jwt(secret, 60, past)
    val now    = TokenService.jwt(secret, 60)
    issuer.issue(42).flatMap(now.verify).map(result => assertEquals(result, None))
  }

  test("garbage is rejected") {
    TokenService.jwt(secret, 60).verify("not-a-jwt").map(result => assertEquals(result, None))
  }
}

class AuthServiceSuite extends CatsEffectSuite {

  private def makeService: IO[AuthService] =
    InMemoryUserRepository.make.map { users =>
      AuthService.live(
        users,
        PasswordHasher.bcrypt(cost = 4),
        TokenService.jwt("test-secret-test-secret-test-secret!", 3600)
      )
    }

  test("register -> login -> authenticate") {
    for {
      service <- makeService
      user    <- service.register(RegisterRequest("alice", "password123"))
      token   <- service.login(LoginRequest("alice", "password123"))
      userId  <- service.authenticate(token.accessToken)
    } yield {
      assertEquals(user.login, "alice")
      assertEquals(userId, user.id)
      assertEquals(token.tokenType, "Bearer")
    }
  }

  test("duplicate login -> Conflict") {
    for {
      service <- makeService
      _       <- service.register(RegisterRequest("alice", "password123"))
      result  <- service.register(RegisterRequest("alice", "password456")).attempt
    } yield assert(result.left.exists(_.isInstanceOf[AppError.Conflict]))
  }

  test("wrong password and unknown user give the same error") {
    for {
      service     <- makeService
      _           <- service.register(RegisterRequest("alice", "password123"))
      wrongPass   <- service.login(LoginRequest("alice", "wrong-password")).attempt
      unknownUser <- service.login(LoginRequest("bob", "password123")).attempt
    } yield {
      assertEquals(wrongPass.left.map(_.getMessage), Left("Invalid login or password"))
      assertEquals(unknownUser.left.map(_.getMessage), Left("Invalid login or password"))
    }
  }

  test("invalid token -> Unauthorized") {
    for {
      service <- makeService
      result  <- service.authenticate("bad").attempt
    } yield assert(result.left.exists(_.isInstanceOf[AppError.Unauthorized]))
  }
}
