package com.example.passwordvault.repository

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.functor.*
import com.example.passwordvault.domain.{NewPasswordRecord, PasswordRecord}
import doobie.*
import doobie.implicits.*

trait PasswordRepository[F[_]] {

  def create(
      userId: Long,
      name: String,
      encryptedPassword: String,
      comment: Option[String],
      created: Long
  ): F[PasswordRecord]

  def createMany(userId: Long, records: List[NewPasswordRecord], created: Long): F[Int]

  def list(userId: Long, limit: Int, offset: Int): F[List[PasswordRecord]]

  def listAll(userId: Long): F[List[PasswordRecord]]

  def search(userId: Long, query: String, exact: Boolean, limit: Int, offset: Int): F[List[PasswordRecord]]

  def get(userId: Long, id: Long): F[Option[PasswordRecord]]

  def update(
      userId: Long,
      id: Long,
      name: Option[String],
      encryptedPassword: Option[String],
      comment: Option[Option[String]]
  ): F[Option[PasswordRecord]]

  def delete(userId: Long, id: Long, deleted: Long): F[Boolean]
}

object PasswordRepository {

  def postgres[F[_]: MonadCancelThrow](xa: Transactor[F]): PasswordRepository[F] =
    new PasswordRepository[F] {

      private val selectColumns =
        fr"SELECT id, user_id, name, encrypted_password, comment, created, deleted FROM password_entries"

      override def create(
          userId: Long,
          name: String,
          encryptedPassword: String,
          comment: Option[String],
          created: Long
      ): F[PasswordRecord] =
        sql"""
          INSERT INTO password_entries (user_id, name, encrypted_password, comment, created)
          VALUES ($userId, $name, $encryptedPassword, $comment, $created)
          RETURNING id, user_id, name, encrypted_password, comment, created, deleted
        """
          .query[PasswordRecord]
          .unique
          .transact(xa)

      override def createMany(userId: Long, records: List[NewPasswordRecord], created: Long): F[Int] =
        Update[(Long, String, String, Option[String], Long)](
          """
            INSERT INTO password_entries (user_id, name, encrypted_password, comment, created)
            VALUES (?, ?, ?, ?, ?)
          """
        ).updateMany(records.map(r => (userId, r.name, r.encryptedPassword, r.comment, created)))
          .transact(xa)

      override def list(userId: Long, limit: Int, offset: Int): F[List[PasswordRecord]] =
        (selectColumns ++
          fr"WHERE user_id = $userId AND deleted IS NULL ORDER BY id ASC LIMIT $limit OFFSET $offset")
          .query[PasswordRecord]
          .to[List]
          .transact(xa)

      override def listAll(userId: Long): F[List[PasswordRecord]] =
        (selectColumns ++ fr"WHERE user_id = $userId AND deleted IS NULL ORDER BY id ASC")
          .query[PasswordRecord]
          .to[List]
          .transact(xa)

      override def search(
          userId: Long,
          query: String,
          exact: Boolean,
          limit: Int,
          offset: Int
      ): F[List[PasswordRecord]] = {
        val condition =
          if (exact) fr"name = $query"
          else fr"name ILIKE ${s"%$query%"}"

        (selectColumns ++
          fr"WHERE user_id = $userId AND deleted IS NULL AND" ++ condition ++
          fr"ORDER BY id ASC LIMIT $limit OFFSET $offset")
          .query[PasswordRecord]
          .to[List]
          .transact(xa)
      }

      override def get(userId: Long, id: Long): F[Option[PasswordRecord]] =
        (selectColumns ++ fr"WHERE id = $id AND user_id = $userId AND deleted IS NULL")
          .query[PasswordRecord]
          .option
          .transact(xa)

      override def update(
          userId: Long,
          id: Long,
          name: Option[String],
          encryptedPassword: Option[String],
          comment: Option[Option[String]]
      ): F[Option[PasswordRecord]] = {
        val updateComment = comment.isDefined
        val commentValue  = comment.flatten

        sql"""
          UPDATE password_entries
          SET
            name = COALESCE($name, name),
            encrypted_password = COALESCE($encryptedPassword, encrypted_password),
            comment =
              CASE
                WHEN $updateComment THEN $commentValue
                ELSE comment
              END
          WHERE id = $id
            AND user_id = $userId
            AND deleted IS NULL
          RETURNING id, user_id, name, encrypted_password, comment, created, deleted
        """
          .query[PasswordRecord]
          .option
          .transact(xa)
      }

      override def delete(userId: Long, id: Long, deleted: Long): F[Boolean] =
        sql"""
          UPDATE password_entries
          SET deleted = $deleted
          WHERE id = $id
            AND user_id = $userId
            AND deleted IS NULL
        """.update.run
          .transact(xa)
          .map(_ > 0)
    }
}
