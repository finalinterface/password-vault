package com.example.passwordvault.service

import com.example.passwordvault.domain.{AppError, CreatePasswordRequest}
import com.example.passwordvault.service.CsvFormat.ExportRow
import munit.CatsEffectSuite

class CsvFormatSuite extends CatsEffectSuite {

  test("values with commas, quotes and newlines survive export -> import") {
    val rows = List(
      ExportRow(1, "GitHub", "a,b", Some("dev account"), 100),
      ExportRow(2, "Bank \"main\"", "p\"q", None, 200),
      ExportRow(3, "Multi", "line1\nline2", Some("comment, with comma"), 300)
    )

    for {
      csv    <- CsvFormat.render(rows)
      parsed <- CsvFormat.parse(csv)
    } yield assertEquals(
      parsed,
      List(
        CreatePasswordRequest("GitHub", "a,b", Some("dev account")),
        CreatePasswordRequest("Bank \"main\"", "p\"q", None),
        CreatePasswordRequest("Multi", "line1\nline2", Some("comment, with comma"))
      )
    )
  }

  test("export of empty list still has a header") {
    CsvFormat.render(Nil).map(csv => assert(csv.startsWith("id,name,password,comment,created")))
  }

  test("full export format with id and created is accepted on import") {
    val csv =
      """id,name,password,comment,created
        |25,GitHub,secret123,dev account,1780256016
        |27,Amazon,amz789,,1780256027
        |""".stripMargin

    CsvFormat.parse(csv).map { rows =>
      assertEquals(
        rows,
        List(
          CreatePasswordRequest("GitHub", "secret123", Some("dev account")),
          CreatePasswordRequest("Amazon", "amz789", None)
        )
      )
    }
  }

  test("minimal format without id and comment") {
    CsvFormat.parse("name,password\nGitHub,secret\n").map { rows =>
      assertEquals(rows, List(CreatePasswordRequest("GitHub", "secret", None)))
    }
  }

  test("windows line endings") {
    CsvFormat.parse("name,password,comment\r\nGitHub,secret,c\r\n").map { rows =>
      assertEquals(rows, List(CreatePasswordRequest("GitHub", "secret", Some("c"))))
    }
  }

  test("invalid rows are reported with line numbers") {
    val csv = "name,password\nGitHub,secret\n ,secret\nAmazon,\n"

    CsvFormat.parse(csv).attempt.map {
      case Left(AppError.ValidationFailed(errors)) =>
        assertEquals(errors.size, 2)
        assert(errors.exists(_.startsWith("line 3")), errors)
        assert(errors.exists(_.startsWith("line 4")), errors)
      case other => fail(s"expected validation error, got $other")
    }
  }

  test("missing password column") {
    CsvFormat.parse("name,comment\nGitHub,c\n").attempt.map {
      case Left(AppError.ValidationFailed(errors)) =>
        assert(errors.head.contains("password"), errors)
      case other => fail(s"expected validation error, got $other")
    }
  }

  test("row with wrong number of columns is an error, not a crash") {
    CsvFormat.parse("name,password,comment\nGitHub\n").attempt.map {
      case Left(AppError.ValidationFailed(errors)) => assert(errors.head.startsWith("Invalid CSV"))
      case other                                   => fail(s"expected validation error, got $other")
    }
  }
}
