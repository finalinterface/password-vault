package com.example.passwordvault.repository

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.example.passwordvault.config.DatabaseConfig
import com.example.passwordvault.db.Database
import com.example.passwordvault.domain.NewPasswordRecord
import doobie.Transactor
import munit.CatsEffectSuite
import org.testcontainers.containers.PostgreSQLContainer

import java.util.UUID

class PostgresRepositorySuite extends CatsEffectSuite {

  private val postgres: Resource[IO, Transactor[IO]] =
    Resource
      .make(IO.blocking {
        val container = new PostgreSQLContainer("postgres:16")
        container.start()
        container
      })(container => IO.blocking(container.stop()))
      .flatMap { container =>
        val config = DatabaseConfig(container.getJdbcUrl, container.getUsername, container.getPassword, 4)
        Resource.eval(Database.migrate(config)) >> Database.transactor(config)
      }

  private val db = ResourceSuiteLocalFixture("postgres", postgres)

  override def munitFixtures = List(db)

  private def users     = UserRepository.postgres[IO](db())
  private def passwords = PasswordRepository.postgres[IO](db())

  private def newUser: IO[Long] =
    users.create(s"user-${UUID.randomUUID().toString.take(8)}", "hash", 0L).map(_.get.id)

  test("create and get") {
    for {
      userId  <- newUser
      created <- passwords.create(userId, "GitHub", "enc", Some("comment"), 100L)
      found   <- passwords.get(userId, created.id)
    } yield {
      assertEquals(found, Some(created))
      assertEquals(created.userId, userId)
      assertEquals(created.deleted, None)
    }
  }

  test("entries are isolated between users") {
    for {
      alice   <- newUser
      bob     <- newUser
      created <- passwords.create(alice, "GitHub", "enc", None, 100L)
      bobGet  <- passwords.get(bob, created.id)
      bobList <- passwords.list(bob, 50, 0)
      bobUpd  <- passwords.update(bob, created.id, Some("hacked"), None, None)
      bobDel  <- passwords.delete(bob, created.id, 200L)
    } yield {
      assertEquals(bobGet, None)
      assertEquals(bobList, Nil)
      assertEquals(bobUpd, None)
      assertEquals(bobDel, false)
    }
  }

  test("soft delete hides entry and keeps the row") {
    for {
      userId  <- newUser
      created <- passwords.create(userId, "GitHub", "enc", None, 100L)
      first   <- passwords.delete(userId, created.id, 200L)
      second  <- passwords.delete(userId, created.id, 300L)
      found   <- passwords.get(userId, created.id)
    } yield {
      assert(first)
      assert(!second)
      assertEquals(found, None)
    }
  }

  test("update: comment absent / null / value") {
    for {
      userId    <- newUser
      created   <- passwords.create(userId, "GitHub", "enc", Some("old"), 100L)
      untouched <- passwords.update(userId, created.id, Some("GitHub2"), None, None)
      replaced  <- passwords.update(userId, created.id, None, None, Some(Some("new")))
      cleared   <- passwords.update(userId, created.id, None, Some("enc2"), Some(None))
    } yield {
      assertEquals(untouched.map(r => (r.name, r.comment)), Some(("GitHub2", Some("old"))))
      assertEquals(replaced.flatMap(_.comment), Some("new"))
      assertEquals(cleared.map(r => (r.comment, r.encryptedPassword)), Some((None, "enc2")))
    }
  }

  test("search partial (case insensitive) and exact") {
    for {
      userId  <- newUser
      _       <- passwords.create(userId, "GitHub", "enc", None, 100L)
      _       <- passwords.create(userId, "GitLab", "enc", None, 100L)
      _       <- passwords.create(userId, "Amazon", "enc", None, 100L)
      partial <- passwords.search(userId, "git", exact = false, 50, 0)
      exact   <- passwords.search(userId, "GitHub", exact = true, 50, 0)
      none    <- passwords.search(userId, "github", exact = true, 50, 0)
    } yield {
      assertEquals(partial.map(_.name), List("GitHub", "GitLab"))
      assertEquals(exact.map(_.name), List("GitHub"))
      assertEquals(none, Nil)
    }
  }

  test("pagination") {
    for {
      userId <- newUser
      _      <- passwords.create(userId, "a", "enc", None, 100L)
      _      <- passwords.create(userId, "b", "enc", None, 100L)
      _      <- passwords.create(userId, "c", "enc", None, 100L)
      page1  <- passwords.list(userId, 2, 0)
      page2  <- passwords.list(userId, 2, 2)
    } yield {
      assertEquals(page1.map(_.name), List("a", "b"))
      assertEquals(page2.map(_.name), List("c"))
    }
  }

  test("createMany is atomic: one bad row -> nothing inserted") {
    val rows = List(
      NewPasswordRecord("ok", "enc", None),
      NewPasswordRecord(" ", "enc", None)
    )
    for {
      userId <- newUser
      result <- passwords.createMany(userId, rows, 100L).attempt
      all    <- passwords.listAll(userId)
    } yield {
      assert(result.isLeft)
      assertEquals(all, Nil)
    }
  }

  test("createMany inserts all rows") {
    val rows = List(NewPasswordRecord("a", "enc", None), NewPasswordRecord("b", "enc", Some("c")))
    for {
      userId   <- newUser
      inserted <- passwords.createMany(userId, rows, 100L)
      all      <- passwords.listAll(userId)
    } yield {
      assertEquals(inserted, 2)
      assertEquals(all.map(r => (r.name, r.comment)), List(("a", None), ("b", Some("c"))))
    }
  }

  test("duplicate login -> None") {
    val login = s"dup-${UUID.randomUUID().toString.take(8)}"
    for {
      first  <- users.create(login, "hash", 0L)
      second <- users.create(login, "hash", 0L)
      found  <- users.findByLogin(login)
    } yield {
      assert(first.isDefined)
      assertEquals(second, None)
      assertEquals(found, first)
    }
  }
}
