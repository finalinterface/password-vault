package com.example.passwordvault.domain

import io.circe.parser.decode
import munit.FunSuite

class UpdatePasswordRequestSuite extends FunSuite {

  test("absent comment means 'do not touch'") {
    assertEquals(
      decode[UpdatePasswordRequest]("""{"name":"GitHub"}"""),
      Right(UpdatePasswordRequest(Some("GitHub"), None, None))
    )
  }

  test("null comment means 'clear'") {
    assertEquals(
      decode[UpdatePasswordRequest]("""{"comment":null}"""),
      Right(UpdatePasswordRequest(None, None, Some(None)))
    )
  }

  test("string comment means 'replace'") {
    assertEquals(
      decode[UpdatePasswordRequest]("""{"comment":"new"}"""),
      Right(UpdatePasswordRequest(None, None, Some(Some("new"))))
    )
  }

  test("comment of wrong type is an error") {
    assert(decode[UpdatePasswordRequest]("""{"comment":42}""").isLeft)
  }
}

class ValidationSuite extends FunSuite {

  test("valid create request passes and name is trimmed") {
    val result = Validation.create(CreatePasswordRequest("  GitHub ", "secret", None))
    assertEquals(result.toEither, Right(CreatePasswordRequest("GitHub", "secret", None)))
  }

  test("all errors are collected, not only the first one") {
    val result = Validation.create(CreatePasswordRequest(" ", "", Some("x" * 2000)))
    assertEquals(result.toEither.left.map(_.length), Left(3L))
  }

  test("empty update is rejected") {
    assert(Validation.update(UpdatePasswordRequest(None, None, None)).isInvalid)
  }

  test("update with only comment cleared is fine") {
    assert(Validation.update(UpdatePasswordRequest(None, None, Some(None))).isValid)
  }

  test("register: login and password rules") {
    assert(Validation.register(RegisterRequest("alice", "longenough")).isValid)
    assert(Validation.register(RegisterRequest("al", "longenough")).isInvalid)
    assert(Validation.register(RegisterRequest("alice smith", "longenough")).isInvalid)
    assert(Validation.register(RegisterRequest("alice", "short")).isInvalid)
    assert(Validation.register(RegisterRequest("alice", "я" * 40)).isInvalid)
  }

  test("pagination bounds") {
    assert(Validation.pagination(50, 0).isValid)
    assert(Validation.pagination(0, 0).isInvalid)
    assert(Validation.pagination(101, 0).isInvalid)
    assert(Validation.pagination(10, -1).isInvalid)
  }
}
