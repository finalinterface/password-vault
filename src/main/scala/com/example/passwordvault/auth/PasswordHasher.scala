package com.example.passwordvault.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import cats.effect.IO

trait PasswordHasher {
  def hash(password: String): IO[String]
  def verify(password: String, hash: String): IO[Boolean]
}

object PasswordHasher {

  def bcrypt(cost: Int = 12): PasswordHasher =
    new PasswordHasher {
      override def hash(password: String): IO[String] =
        IO.blocking(BCrypt.withDefaults().hashToString(cost, password.toCharArray))

      override def verify(password: String, hash: String): IO[Boolean] =
        IO.blocking(BCrypt.verifyer().verify(password.toCharArray, hash).verified)
    }
}
