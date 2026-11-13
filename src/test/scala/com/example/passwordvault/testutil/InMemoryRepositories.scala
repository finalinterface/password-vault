package com.example.passwordvault.testutil

import cats.effect.{IO, Ref}
import com.example.passwordvault.crypto.Crypto
import com.example.passwordvault.domain.{NewPasswordRecord, PasswordRecord, User}
import com.example.passwordvault.repository.{PasswordRepository, UserRepository}

import java.security.SecureRandom
import java.util.Base64

object TestKeys {
  def randomMasterKey(): String = {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }

  def crypto: IO[Crypto] = Crypto.aesGcm(randomMasterKey())
}

final class InMemoryPasswordRepository(state: Ref[IO, Vector[PasswordRecord]])
    extends PasswordRepository[IO] {

  private def nextId(records: Vector[PasswordRecord]): Long =
    records.map(_.id).maxOption.getOrElse(0L) + 1

  private def active(userId: Long, records: Vector[PasswordRecord]): Vector[PasswordRecord] =
    records.filter(r => r.userId == userId && r.deleted.isEmpty).sortBy(_.id)

  override def create(
      userId: Long,
      name: String,
      encryptedPassword: String,
      comment: Option[String],
      created: Long
  ): IO[PasswordRecord] =
    state.modify { records =>
      val record = PasswordRecord(nextId(records), userId, name, encryptedPassword, comment, created, None)
      (records :+ record, record)
    }

  override def createMany(userId: Long, newRecords: List[NewPasswordRecord], created: Long): IO[Int] =
    state.modify { records =>
      val start = nextId(records)
      val added = newRecords.zipWithIndex.map { case (r, i) =>
        PasswordRecord(start + i, userId, r.name, r.encryptedPassword, r.comment, created, None)
      }
      (records ++ added, added.size)
    }

  override def list(userId: Long, limit: Int, offset: Int): IO[List[PasswordRecord]] =
    state.get.map(rs => active(userId, rs).slice(offset, offset + limit).toList)

  override def listAll(userId: Long): IO[List[PasswordRecord]] =
    state.get.map(rs => active(userId, rs).toList)

  override def search(
      userId: Long,
      query: String,
      exact: Boolean,
      limit: Int,
      offset: Int
  ): IO[List[PasswordRecord]] =
    state.get.map { rs =>
      active(userId, rs)
        .filter(r => if (exact) r.name == query else r.name.toLowerCase.contains(query.toLowerCase))
        .slice(offset, offset + limit)
        .toList
    }

  override def get(userId: Long, id: Long): IO[Option[PasswordRecord]] =
    state.get.map(rs => active(userId, rs).find(_.id == id))

  override def update(
      userId: Long,
      id: Long,
      name: Option[String],
      encryptedPassword: Option[String],
      comment: Option[Option[String]]
  ): IO[Option[PasswordRecord]] =
    state.modify { records =>
      active(userId, records).find(_.id == id) match {
        case None => (records, None)
        case Some(old) =>
          val updated = old.copy(
            name = name.getOrElse(old.name),
            encryptedPassword = encryptedPassword.getOrElse(old.encryptedPassword),
            comment = comment.getOrElse(old.comment)
          )
          (records.map(r => if (r.id == id) updated else r), Some(updated))
      }
    }

  override def delete(userId: Long, id: Long, deleted: Long): IO[Boolean] =
    state.modify { records =>
      active(userId, records).find(_.id == id) match {
        case None    => (records, false)
        case Some(_) => (records.map(r => if (r.id == id) r.copy(deleted = Some(deleted)) else r), true)
      }
    }
}

object InMemoryPasswordRepository {
  def make: IO[InMemoryPasswordRepository] =
    Ref.of[IO, Vector[PasswordRecord]](Vector.empty).map(new InMemoryPasswordRepository(_))
}

final class InMemoryUserRepository(state: Ref[IO, Vector[User]]) extends UserRepository[IO] {

  override def create(login: String, passwordHash: String, created: Long): IO[Option[User]] =
    state.modify { users =>
      if (users.exists(_.login == login)) (users, None)
      else {
        val user = User(users.size.toLong + 1, login, passwordHash, created)
        (users :+ user, Some(user))
      }
    }

  override def findByLogin(login: String): IO[Option[User]] =
    state.get.map(_.find(_.login == login))
}

object InMemoryUserRepository {
  def make: IO[InMemoryUserRepository] =
    Ref.of[IO, Vector[User]](Vector.empty).map(new InMemoryUserRepository(_))
}
