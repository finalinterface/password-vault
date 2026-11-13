package com.example.passwordvault.repository

import cats.effect.kernel.MonadCancelThrow
import com.example.passwordvault.domain.User
import doobie.*
import doobie.implicits.*
import doobie.postgres.sqlstate

trait UserRepository[F[_]] {

  def create(login: String, passwordHash: String, created: Long): F[Option[User]]

  def findByLogin(login: String): F[Option[User]]
}

object UserRepository {

  def postgres[F[_]: MonadCancelThrow](xa: Transactor[F]): UserRepository[F] =
    new UserRepository[F] {

      override def create(login: String, passwordHash: String, created: Long): F[Option[User]] =
        sql"""
          INSERT INTO users (login, password_hash, created)
          VALUES ($login, $passwordHash, $created)
          RETURNING id, login, password_hash, created
        """
          .query[User]
          .unique
          .attemptSomeSqlState { case sqlstate.class23.UNIQUE_VIOLATION => () }
          .map(_.toOption)
          .transact(xa)

      override def findByLogin(login: String): F[Option[User]] =
        sql"""
          SELECT id, login, password_hash, created
          FROM users
          WHERE login = $login
        """
          .query[User]
          .option
          .transact(xa)
    }
}
