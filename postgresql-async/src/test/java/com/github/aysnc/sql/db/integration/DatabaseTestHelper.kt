package com.github.aysnc.sql.db.integration

import com.github.aysnc.sql.db.integration.ContainerHelper.postresql
import com.github.jasync.sql.db.Configuration
import com.github.jasync.sql.db.Connection
import com.github.jasync.sql.db.QueryResult
import com.github.jasync.sql.db.SSLConfiguration
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import io.netty.handler.timeout.TimeoutException
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


open class DatabaseTestHelper {

  private val cotainerHelper = ContainerHelper

  val databaseName = cotainerHelper.defaultConfiguration.database!!
  val conf = cotainerHelper.defaultConfiguration

  fun timeTestDatabase() = "netty_driver_time_test"

  val databasePort = postresql!!.getFirstMappedPort()


  fun timeTestConfiguration() = cotainerHelper.defaultConfiguration.copy(database = timeTestDatabase())

  fun <T> withHandler(fn: (PostgreSQLConnection) -> T): T {
    return withHandler(cotainerHelper.defaultConfiguration, fn)
  }

  fun <T> withTimeHandler(fn: (PostgreSQLConnection) -> T): T {
    return withHandler(this.timeTestConfiguration(), fn)
  }

  fun <T> withSSLHandler(mode: SSLConfiguration.Mode, host: String = "localhost", rootCert: File? = File("script/server.crt"), fn: (PostgreSQLConnection) -> T): T {
    val config = cotainerHelper.defaultConfiguration.copy(
        ssl = SSLConfiguration(mode = mode, rootCert = rootCert))
    return withHandler(config, fn)
  }

  fun <T> withHandler(configuration: Configuration, fn: (PostgreSQLConnection) -> T): T {

    val handler = PostgreSQLConnection(configuration)

    try {
      handler.connect().get(5, TimeUnit.SECONDS)
      return fn(handler)
    } finally {
      handleTimeout(handler) { handler.disconnect() }
    }

  }

  fun executeDdl(handler: Connection, data: String, count: Int = 0): Long {
    val rows = handleTimeout(handler) {
      handler.sendQuery(data).get(5, TimeUnit.SECONDS).rowsAffected
    }

    if (rows.toInt() != count) {
      throw IllegalStateException("We expected %s rows but there were %s".format(count, rows))
    }

    return rows
  }

  private fun <R> handleTimeout(handler: Connection, fn: () -> R): R {
    try {
      return fn()
    } catch (e: TimeoutException) {

      throw IllegalStateException("Timeout executing call from handler -> %s".format(handler))

    }
  }

  fun executeQuery(handler: Connection, data: String): QueryResult {
    return handleTimeout(handler) {
      handler.sendQuery(data).get(5, TimeUnit.SECONDS)
    }
  }

  fun executePreparedStatement(
      handler: Connection,
      statement: String,
      values: List<Any?> = emptyList()): QueryResult {
    return handleTimeout(handler) {
      handler.sendPreparedStatement(statement, values).get(5, TimeUnit.SECONDS)
    }
  }

  fun <T> await(future: CompletableFuture<T>): T {
    return future.get(5, TimeUnit.SECONDS)
  }


}
