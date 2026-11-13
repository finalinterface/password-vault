package com.example.passwordvault.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.Schema

final case class PasswordView(
    id: Long,
    name: String,
    comment: Option[String],
    created: Long
)

object PasswordView {
  given Encoder[PasswordView] = deriveEncoder[PasswordView]
  given Decoder[PasswordView] = deriveDecoder[PasswordView]
  given Schema[PasswordView]  = Schema.derived
}

final case class PasswordSecret(
    id: Long,
    name: String,
    password: String
)

object PasswordSecret {
  given Encoder[PasswordSecret] = deriveEncoder[PasswordSecret]
  given Decoder[PasswordSecret] = deriveDecoder[PasswordSecret]
  given Schema[PasswordSecret]  = Schema.derived
}

final case class CreatePasswordRequest(
    name: String,
    password: String,
    comment: Option[String]
)

object CreatePasswordRequest {
  given Decoder[CreatePasswordRequest] = deriveDecoder[CreatePasswordRequest]
  given Encoder[CreatePasswordRequest] = deriveEncoder[CreatePasswordRequest]
  given Schema[CreatePasswordRequest]  = Schema.derived
}

final case class UpdatePasswordRequest(
    name: Option[String],
    password: Option[String],
    comment: Option[Option[String]]
)

object UpdatePasswordRequest {

  given Decoder[UpdatePasswordRequest] = Decoder.instance { cursor =>
    val commentCursor = cursor.downField("comment")

    val decodedComment: Decoder.Result[Option[Option[String]]] =
      if (!commentCursor.succeeded) {
        Right(None)
      } else {
        commentCursor.focus match {
          case Some(json) if json.isNull =>
            Right(Some(None))

          case _ =>
            commentCursor.as[String].map(value => Some(Some(value)))
        }
      }

    for {
      name     <- cursor.get[Option[String]]("name")
      password <- cursor.get[Option[String]]("password")
      comment  <- decodedComment
    } yield UpdatePasswordRequest(
      name = name,
      password = password,
      comment = comment
    )
  }

  given Encoder[UpdatePasswordRequest] = Encoder.instance { request =>
    import io.circe.Json
    import io.circe.syntax.*

    val fields =
      request.name.map(n => "name" -> n.asJson).toList ++
        request.password.map(p => "password" -> p.asJson).toList ++
        request.comment.map(c => "comment" -> c.fold(Json.Null)(_.asJson)).toList

    Json.obj(fields*)
  }

  given Schema[UpdatePasswordRequest] = Schema.derived
}

final case class ImportResult(imported: Int)

object ImportResult {
  given Encoder[ImportResult] = deriveEncoder[ImportResult]
  given Decoder[ImportResult] = deriveDecoder[ImportResult]
  given Schema[ImportResult]  = Schema.derived
}

final case class PasswordRecord(
    id: Long,
    userId: Long,
    name: String,
    encryptedPassword: String,
    comment: Option[String],
    created: Long,
    deleted: Option[Long]
)

final case class NewPasswordRecord(
    name: String,
    encryptedPassword: String,
    comment: Option[String]
)
