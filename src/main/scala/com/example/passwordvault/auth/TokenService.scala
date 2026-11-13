package com.example.passwordvault.auth

import cats.effect.IO
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Clock

trait TokenService {
  def issue(userId: Long): IO[String]

  def verify(token: String): IO[Option[Long]]

  def ttlSeconds: Long
}

object TokenService {

  def jwt(secret: String, ttl: Long, clock: Clock = Clock.systemUTC()): TokenService =
    new TokenService {
      private given Clock   = clock
      private val algorithm = JwtAlgorithm.HS256

      override val ttlSeconds: Long = ttl

      override def issue(userId: Long): IO[String] =
        IO {
          val claim = JwtClaim(subject = Some(userId.toString)).issuedNow.expiresIn(ttl)
          JwtCirce.encode(claim, secret, algorithm)
        }

      override def verify(token: String): IO[Option[Long]] =
        IO {
          JwtCirce
            .decode(token, secret, Seq(algorithm))
            .toOption
            .flatMap(_.subject)
            .flatMap(_.toLongOption)
        }
    }
}
