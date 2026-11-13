package com.example.passwordvault.db

import cats.effect.{IO, Resource}
import com.example.passwordvault.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

object Database {

  def transactor(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setJdbcUrl(config.url)
    hikariConfig.setUsername(config.user)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.poolSize)

    HikariTransactor.fromHikariConfig[IO](hikariConfig)
  }

  def migrate(config: DatabaseConfig): IO[Unit] =
    IO.blocking {
      Flyway
        .configure()
        .dataSource(config.url, config.user, config.password)
        .locations("classpath:db/migration")
        .load()
        .migrate()
    }.void
}
