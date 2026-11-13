package com.example.passwordvault.http

import com.example.passwordvault.domain.*
import sttp.model.{MediaType, StatusCode}
import sttp.tapir.*
import sttp.tapir.json.circe.*

object Endpoints {

  type ApiError = (StatusCode, ErrorResponse)

  private object TextCsv extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }

  private val base = endpoint.errorOut(statusCode.and(jsonBody[ErrorResponse]))

  private val secured =
    base.securityIn(auth.bearer[String]().description("JWT access token"))

  private val passwordId = path[Long]("id").description("Password entry id")

  val health: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("health")
      .out(stringBody)
      .description("Health check")

  val register =
    base.post
      .in("auth" / "register")
      .in(jsonBody[RegisterRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[UserView]))
      .description("Register a new user")
      .tag("auth")

  val login =
    base.post
      .in("auth" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[TokenResponse])
      .description("Get access token")
      .tag("auth")

  val listPasswords =
    secured.get
      .in("passwords")
      .in(query[Option[String]]("search").description("Search by name"))
      .in(query[Boolean]("exact").default(false).description("Exact name match"))
      .in(query[Int]("limit").default(50))
      .in(query[Int]("offset").default(0))
      .out(jsonBody[List[PasswordView]])
      .description("List entries (without passwords)")
      .tag("passwords")

  val createPassword =
    secured.post
      .in("passwords")
      .in(jsonBody[CreatePasswordRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[PasswordView]))
      .description("Create a new entry")
      .tag("passwords")

  val exportCsv =
    secured.get
      .in("passwords" / "export")
      .out(header("Content-Disposition", "attachment; filename=\"passwords.csv\""))
      .out(stringBodyUtf8AnyFormat(Codec.string.format(TextCsv)))
      .description("Export all entries as CSV (with passwords!)")
      .tag("passwords")

  val importCsv =
    secured.post
      .in("passwords" / "import")
      .in(stringBody.description("CSV with header: name,password,comment (id, created are ignored)"))
      .out(jsonBody[ImportResult])
      .description("Import entries from CSV. All or nothing: if any row is invalid, nothing is imported")
      .tag("passwords")

  val getPassword =
    secured.get
      .in("passwords" / passwordId)
      .out(jsonBody[PasswordView])
      .description("Get entry by id (without password)")
      .tag("passwords")

  val getSecret =
    secured.get
      .in("passwords" / passwordId / "secret")
      .out(jsonBody[PasswordSecret])
      .description("Get decrypted password")
      .tag("passwords")

  val updatePassword =
    secured.patch
      .in("passwords" / passwordId)
      .in(jsonBody[UpdatePasswordRequest])
      .out(jsonBody[PasswordView])
      .description("Partial update. comment: null clears the comment")
      .tag("passwords")

  val deletePassword =
    secured.delete
      .in("passwords" / passwordId)
      .out(statusCode(StatusCode.NoContent))
      .description("Delete entry (soft delete)")
      .tag("passwords")
}
