package com.example.passwordvault.service

import cats.effect.IO
import com.example.passwordvault.domain.*
import com.example.passwordvault.testutil.{InMemoryPasswordRepository, TestKeys}
import munit.CatsEffectSuite

class PasswordServiceSuite extends CatsEffectSuite {

  private val alice = 1L
  private val bob   = 2L

  private def makeService: IO[(PasswordService, InMemoryPasswordRepository)] =
    for {
      repo   <- InMemoryPasswordRepository.make
      crypto <- TestKeys.crypto
    } yield (PasswordService.live(repo, crypto), repo)

  test("password is stored encrypted and returned only via secret") {
    for {
      env <- makeService
      (service, repo) = env
      view   <- service.create(alice, CreatePasswordRequest("GitHub", "secret123", None))
      stored <- repo.get(alice, view.id)
      secret <- service.secret(alice, view.id)
    } yield {
      assertNotEquals(stored.map(_.encryptedPassword), Some("secret123"))
      assertEquals(secret, PasswordSecret(view.id, "GitHub", "secret123"))
    }
  }

  test("user can't see other user's entries") {
    for {
      service   <- makeService.map(_._1)
      view      <- service.create(alice, CreatePasswordRequest("GitHub", "secret", None))
      bobList   <- service.list(bob, None, exact = false, limit = 50, offset = 0)
      bobGet    <- service.get(bob, view.id).attempt
      bobSecret <- service.secret(bob, view.id).attempt
      bobDelete <- service.delete(bob, view.id).attempt
      aliceGet  <- service.get(alice, view.id)
    } yield {
      assertEquals(bobList, Nil)
      assert(bobGet.left.exists(_.isInstanceOf[AppError.NotFound]))
      assert(bobSecret.left.exists(_.isInstanceOf[AppError.NotFound]))
      assert(bobDelete.left.exists(_.isInstanceOf[AppError.NotFound]))
      assertEquals(aliceGet, view)
    }
  }

  test("invalid create request -> ValidationFailed") {
    for {
      service <- makeService.map(_._1)
      result  <- service.create(alice, CreatePasswordRequest("", "", None)).attempt
    } yield result match {
      case Left(AppError.ValidationFailed(errors)) => assertEquals(errors.size, 2)
      case other                                   => fail(s"unexpected $other")
    }
  }

  test("update changes only provided fields") {
    for {
      service <- makeService.map(_._1)
      view    <- service.create(alice, CreatePasswordRequest("GitHub", "old", Some("comment")))
      updated <- service.update(alice, view.id, UpdatePasswordRequest(None, Some("new"), None))
      secret  <- service.secret(alice, view.id)
      cleared <- service.update(alice, view.id, UpdatePasswordRequest(None, None, Some(None)))
    } yield {
      assertEquals(updated.name, "GitHub")
      assertEquals(updated.comment, Some("comment"))
      assertEquals(secret.password, "new")
      assertEquals(cleared.comment, None)
    }
  }

  test("deleted entry is not visible anymore") {
    for {
      service <- makeService.map(_._1)
      view    <- service.create(alice, CreatePasswordRequest("GitHub", "secret", None))
      _       <- service.delete(alice, view.id)
      list    <- service.list(alice, None, exact = false, limit = 50, offset = 0)
      second  <- service.delete(alice, view.id).attempt
    } yield {
      assertEquals(list, Nil)
      assert(second.isLeft)
    }
  }

  test("search partial and exact") {
    for {
      service <- makeService.map(_._1)
      _       <- service.create(alice, CreatePasswordRequest("GitHub", "1", None))
      _       <- service.create(alice, CreatePasswordRequest("GitLab", "2", None))
      _       <- service.create(alice, CreatePasswordRequest("Amazon", "3", None))
      partial <- service.list(alice, Some("git"), exact = false, limit = 50, offset = 0)
      exact   <- service.list(alice, Some("GitHub"), exact = true, limit = 50, offset = 0)
    } yield {
      assertEquals(partial.map(_.name), List("GitHub", "GitLab"))
      assertEquals(exact.map(_.name), List("GitHub"))
    }
  }

  test("bad pagination -> ValidationFailed") {
    for {
      service <- makeService.map(_._1)
      result  <- service.list(alice, None, exact = false, limit = 1000, offset = 0).attempt
    } yield assert(result.left.exists(_.isInstanceOf[AppError.ValidationFailed]))
  }

  test("export -> import round trip keeps passwords") {
    for {
      service <- makeService.map(_._1)
      _       <- service.create(alice, CreatePasswordRequest("GitHub", "a,b\"c", Some("x, y")))
      csv     <- service.exportCsv(alice)
      result  <- service.importCsv(bob, csv)
      bobList <- service.list(bob, None, exact = false, limit = 50, offset = 0)
      secret  <- service.secret(bob, bobList.head.id)
    } yield {
      assertEquals(result, ImportResult(1))
      assertEquals(bobList.map(v => (v.name, v.comment)), List(("GitHub", Some("x, y"))))
      assertEquals(secret.password, "a,b\"c")
    }
  }

  test("import with one bad row imports nothing") {
    val csv = "name,password\nGitHub,secret\n,nope\n"
    for {
      service <- makeService.map(_._1)
      result  <- service.importCsv(alice, csv).attempt
      list    <- service.list(alice, None, exact = false, limit = 50, offset = 0)
    } yield {
      assert(result.left.exists(_.isInstanceOf[AppError.ValidationFailed]))
      assertEquals(list, Nil)
    }
  }
}
