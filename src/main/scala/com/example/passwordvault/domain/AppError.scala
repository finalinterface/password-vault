package com.example.passwordvault.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

sealed abstract class AppError(val message: String) extends Exception(message)

object AppError {
  final case class NotFound(what: String) extends AppError(s"$what was not found")

  final case class ValidationFailed(errors: List[String]) extends AppError("Validation failed")

  final case class Unauthorized(reason: String) extends AppError(reason)

  final case class Conflict(reason: String) extends AppError(reason)
}

final case class ErrorResponse(error: String, details: List[String] = Nil)

object ErrorResponse {
  given Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
  given Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  given Schema[ErrorResponse]  = Schema.derived
}
