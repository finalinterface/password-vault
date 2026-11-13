package com.example.passwordvault.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

final case class User(
    id: Long,
    login: String,
    passwordHash: String,
    created: Long
)

final case class UserView(id: Long, login: String)

object UserView {
  given Encoder[UserView] = deriveEncoder[UserView]
  given Decoder[UserView] = deriveDecoder[UserView]
  given Schema[UserView]  = Schema.derived
}

final case class RegisterRequest(login: String, password: String)

object RegisterRequest {
  given Decoder[RegisterRequest] = deriveDecoder[RegisterRequest]
  given Encoder[RegisterRequest] = deriveEncoder[RegisterRequest]
  given Schema[RegisterRequest]  = Schema.derived
}

final case class LoginRequest(login: String, password: String)

object LoginRequest {
  given Decoder[LoginRequest] = deriveDecoder[LoginRequest]
  given Encoder[LoginRequest] = deriveEncoder[LoginRequest]
  given Schema[LoginRequest]  = Schema.derived
}

final case class TokenResponse(
    accessToken: String,
    tokenType: String,
    expiresIn: Long
)

object TokenResponse {
  given Encoder[TokenResponse] = deriveEncoder[TokenResponse]
  given Decoder[TokenResponse] = deriveDecoder[TokenResponse]
  given Schema[TokenResponse]  = Schema.derived
}
