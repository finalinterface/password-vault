package com.example.passwordvault.service

import cats.effect.IO
import cats.syntax.traverse.*
import com.example.passwordvault.crypto.Crypto
import com.example.passwordvault.domain.*
import com.example.passwordvault.domain.Validation.orRaise
import com.example.passwordvault.repository.PasswordRepository

trait PasswordService {

  def create(userId: Long, request: CreatePasswordRequest): IO[PasswordView]

  def list(
      userId: Long,
      search: Option[String],
      exact: Boolean,
      limit: Int,
      offset: Int
  ): IO[List[PasswordView]]

  def get(userId: Long, id: Long): IO[PasswordView]

  def secret(userId: Long, id: Long): IO[PasswordSecret]

  def update(userId: Long, id: Long, request: UpdatePasswordRequest): IO[PasswordView]

  def delete(userId: Long, id: Long): IO[Unit]

  def exportCsv(userId: Long): IO[String]

  def importCsv(userId: Long, csvContent: String): IO[ImportResult]
}

object PasswordService {

  def live(
      repository: PasswordRepository[IO],
      crypto: Crypto
  ): PasswordService =
    new PasswordService {

      override def create(userId: Long, request: CreatePasswordRequest): IO[PasswordView] =
        for {
          valid             <- IO.fromEither(Validation.create(request).orRaise)
          now               <- nowSeconds
          encryptedPassword <- crypto.encrypt(valid.password)
          record <- repository.create(
            userId = userId,
            name = valid.name,
            encryptedPassword = encryptedPassword,
            comment = valid.comment,
            created = now
          )
        } yield toView(record)

      override def list(
          userId: Long,
          search: Option[String],
          exact: Boolean,
          limit: Int,
          offset: Int
      ): IO[List[PasswordView]] =
        for {
          _ <- IO.fromEither(Validation.pagination(limit, offset).orRaise)
          records <- search match {
            case Some(query) => repository.search(userId, query, exact, limit, offset)
            case None        => repository.list(userId, limit, offset)
          }
        } yield records.map(toView)

      override def get(userId: Long, id: Long): IO[PasswordView] =
        findRecord(userId, id).map(toView)

      override def secret(userId: Long, id: Long): IO[PasswordSecret] =
        for {
          record   <- findRecord(userId, id)
          password <- crypto.decrypt(record.encryptedPassword)
        } yield PasswordSecret(record.id, record.name, password)

      override def update(userId: Long, id: Long, request: UpdatePasswordRequest): IO[PasswordView] =
        for {
          valid             <- IO.fromEither(Validation.update(request).orRaise)
          encryptedPassword <- valid.password.traverse(crypto.encrypt)
          updated <- repository.update(
            userId = userId,
            id = id,
            name = valid.name,
            encryptedPassword = encryptedPassword,
            comment = valid.comment
          )
          record <- IO.fromOption(updated)(notFound(id))
        } yield toView(record)

      override def delete(userId: Long, id: Long): IO[Unit] =
        for {
          now     <- nowSeconds
          deleted <- repository.delete(userId = userId, id = id, deleted = now)
          _       <- IO.raiseUnless(deleted)(notFound(id))
        } yield ()

      override def exportCsv(userId: Long): IO[String] =
        for {
          records <- repository.listAll(userId)
          rows <- records.traverse { r =>
            crypto
              .decrypt(r.encryptedPassword)
              .map(pw => CsvFormat.ExportRow(r.id, r.name, pw, r.comment, r.created))
          }
          csv <- CsvFormat.render(rows)
        } yield csv

      override def importCsv(userId: Long, csvContent: String): IO[ImportResult] =
        for {
          requests <- CsvFormat.parse(csvContent)
          records <- requests.traverse { r =>
            crypto.encrypt(r.password).map(enc => NewPasswordRecord(r.name, enc, r.comment))
          }
          now      <- nowSeconds
          imported <- repository.createMany(userId, records, now)
        } yield ImportResult(imported)

      private def findRecord(userId: Long, id: Long): IO[PasswordRecord] =
        repository.get(userId, id).flatMap(r => IO.fromOption(r)(notFound(id)))

      private def notFound(id: Long): AppError =
        AppError.NotFound(s"Password entry with id=$id")

      private def toView(record: PasswordRecord): PasswordView =
        PasswordView(
          id = record.id,
          name = record.name,
          comment = record.comment,
          created = record.created
        )

      private def nowSeconds: IO[Long] =
        IO.realTime.map(_.toSeconds)
    }
}
