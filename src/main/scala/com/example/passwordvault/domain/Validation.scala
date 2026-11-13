package com.example.passwordvault.domain

import cats.data.ValidatedNec
import cats.syntax.all.*

import java.nio.charset.StandardCharsets

object Validation {

  type Result[A] = ValidatedNec[String, A]

  val MaxNameLength     = 255
  val MaxPasswordLength = 1000
  val MaxCommentLength  = 1000

  private val LoginPattern = "^[a-zA-Z0-9_.-]{3,50}$".r

  def name(value: String): Result[String] =
    if (value.trim.isEmpty) "name must not be empty".invalidNec
    else if (value.length > MaxNameLength) s"name must be at most $MaxNameLength characters".invalidNec
    else value.trim.validNec

  def password(value: String): Result[String] =
    if (value.isEmpty) "password must not be empty".invalidNec
    else if (value.length > MaxPasswordLength)
      s"password must be at most $MaxPasswordLength characters".invalidNec
    else value.validNec

  def comment(value: Option[String]): Result[Option[String]] =
    value match {
      case Some(c) if c.length > MaxCommentLength =>
        s"comment must be at most $MaxCommentLength characters".invalidNec
      case other => other.validNec
    }

  def create(request: CreatePasswordRequest): Result[CreatePasswordRequest] =
    (name(request.name), password(request.password), comment(request.comment))
      .mapN(CreatePasswordRequest.apply)

  def update(request: UpdatePasswordRequest): Result[UpdatePasswordRequest] = {
    val nothingToUpdate =
      if (request.name.isEmpty && request.password.isEmpty && request.comment.isEmpty)
        "at least one field must be provided".invalidNec
      else ().validNec

    (
      nothingToUpdate,
      request.name.traverse(name),
      request.password.traverse(password),
      request.comment.traverse(comment)
    ).mapN((_, n, p, c) => UpdatePasswordRequest(n, p, c))
  }

  def register(request: RegisterRequest): Result[RegisterRequest] = {
    val login =
      if (LoginPattern.matches(request.login)) request.login.validNec
      else "login must be 3-50 characters: letters, digits, '_', '.', '-'".invalidNec

    val pwBytes = request.password.getBytes(StandardCharsets.UTF_8).length
    val pw =
      if (request.password.length < 8) "password must be at least 8 characters".invalidNec
      else if (pwBytes > 72) "password must be at most 72 bytes".invalidNec
      else request.password.validNec

    (login, pw).mapN(RegisterRequest.apply)
  }

  def pagination(limit: Int, offset: Int): Result[(Int, Int)] = {
    val l =
      if (limit < 1 || limit > 100) "limit must be between 1 and 100".invalidNec
      else limit.validNec
    val o =
      if (offset < 0) "offset must be >= 0".invalidNec
      else offset.validNec
    (l, o).tupled
  }

  extension [A](result: Result[A]) {
    def orRaise: Either[AppError, A] =
      result.toEither.leftMap(errors => AppError.ValidationFailed(errors.toList))
  }
}
