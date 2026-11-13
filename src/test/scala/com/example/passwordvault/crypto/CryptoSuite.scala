package com.example.passwordvault.crypto

import com.example.passwordvault.testutil.TestKeys
import munit.CatsEffectSuite

import java.util.Base64

class CryptoSuite extends CatsEffectSuite {

  test("encrypt -> decrypt returns original text") {
    for {
      crypto    <- TestKeys.crypto
      encrypted <- crypto.encrypt("p@ss,word\"1 пароль 🔑")
      decrypted <- crypto.decrypt(encrypted)
    } yield assertEquals(decrypted, "p@ss,word\"1 пароль 🔑")
  }

  test("same text gives different ciphertext (random IV)") {
    for {
      crypto <- TestKeys.crypto
      first  <- crypto.encrypt("secret")
      second <- crypto.encrypt("secret")
    } yield assertNotEquals(first, second)
  }

  test("tampered ciphertext is rejected") {
    for {
      crypto    <- TestKeys.crypto
      encrypted <- crypto.encrypt("secret")
      bytes = Base64.getDecoder.decode(encrypted)
      _     = bytes(bytes.length - 1) = (bytes(bytes.length - 1) ^ 1).toByte
      result <- crypto.decrypt(Base64.getEncoder.encodeToString(bytes)).attempt
    } yield assert(result.isLeft, "GCM tag check should fail")
  }

  test("ciphertext from another key can't be decrypted") {
    for {
      crypto1   <- TestKeys.crypto
      crypto2   <- TestKeys.crypto
      encrypted <- crypto1.encrypt("secret")
      result    <- crypto2.decrypt(encrypted).attempt
    } yield assert(result.isLeft)
  }

  test("master key must be 32 bytes") {
    val shortKey = Base64.getEncoder.encodeToString(new Array[Byte](16))
    Crypto.aesGcm(shortKey).attempt.map(result => assert(result.isLeft))
  }

  test("too short payload is rejected") {
    TestKeys.crypto
      .flatMap(_.decrypt(Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3))))
      .attempt
      .map(result => assert(result.isLeft))
  }
}
