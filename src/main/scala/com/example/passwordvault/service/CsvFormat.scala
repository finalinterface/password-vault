package com.example.passwordvault.service

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import com.example.passwordvault.domain.{AppError, CreatePasswordRequest, Validation}
import fs2.Stream
import fs2.data.csv.*

object CsvFormat {

  final case class ExportRow(
      id: Long,
      name: String,
      password: String,
      comment: Option[String],
      created: Long
  )

  val Header: NonEmptyList[String] = NonEmptyList.of("id", "name", "password", "comment", "created")

  def render(rows: List[ExportRow]): IO[String] = {
    val lines = Header :: rows.map { r =>
      NonEmptyList.of(r.id.toString, r.name, r.password, r.comment.getOrElse(""), r.created.toString)
    }

    Stream
      .emits(lines)
      .covary[IO]
      .through(lowlevel.toStrings[IO]())
      .compile
      .string
  }

  def parse(text: String): IO[List[CreatePasswordRequest]] =
    Stream
      .emit(text)
      .covary[IO]
      .through(lowlevel.rows[IO, String]())
      .through(lowlevel.headers[IO, String])
      .compile
      .toList
      .adaptError { case e: CsvException =>
        AppError.ValidationFailed(List(s"Invalid CSV: ${e.getMessage}"))
      }
      .flatMap { rows =>
        val results = rows.map(parseRow)
        val errors  = results.collect { case Left(e) => e }.flatten

        if (errors.nonEmpty) IO.raiseError(AppError.ValidationFailed(errors))
        else IO.pure(results.collect { case Right(r) => r })
      }

  private def parseRow(row: CsvRow[String]): Either[List[String], CreatePasswordRequest] = {
    val lineNo = row.line.map(_.toString).getOrElse("?")

    val request = for {
      name     <- row("name").toRight("missing column 'name'")
      password <- row("password").toRight("missing column 'password'")
      comment = row("comment").filter(_.nonEmpty)
    } yield CreatePasswordRequest(name, password, comment)

    request match {
      case Left(error) => Left(List(s"line $lineNo: $error"))
      case Right(r) =>
        Validation.create(r).toEither.leftMap(_.toList.map(e => s"line $lineNo: $e"))
    }
  }
}
