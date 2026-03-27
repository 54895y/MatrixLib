package com.y54895.matrixlib.api.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import taboolib.common.Inject
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import java.io.File

@Inject
@RuntimeDependencies(
    RuntimeDependency(
        "!org.slf4j:slf4j-api:2.0.8",
        test = "!org.slf4j.LoggerFactory",
        transitive = false
    ),
    RuntimeDependency(
        "!com.zaxxer:HikariCP:4.0.3",
        test = "!com.zaxxer.hikari.HikariDataSource",
        transitive = false
    )
)
object MatrixJdbcCoreLibraries {

    fun ensureLoaded() {
        // Trigger class loading so TabooLib can resolve JDBC runtime libraries before use.
    }
}

@Inject
@RuntimeDependencies(
    RuntimeDependency(
        "!org.xerial:sqlite-jdbc:3.46.1.3",
        test = "!org.sqlite.JDBC",
        transitive = false
    )
)
object MatrixSqliteRuntimeDriver {

    fun ensureLoaded() {
        // Trigger SQLite driver loading on demand.
    }
}

@Inject
@RuntimeDependencies(
    RuntimeDependency(
        "!com.mysql:mysql-connector-j:8.0.33",
        test = "!com.mysql.cj.jdbc.Driver",
        transitive = false
    )
)
object MatrixMySqlRuntimeDriver {

    fun ensureLoaded() {
        // Trigger MySQL driver loading on demand.
    }
}

object MatrixHikariDataSources {

    fun sqlite(
        poolName: String,
        file: File,
        maximumPoolSize: Int = 1,
        minimumIdle: Int = 1,
        connectionTimeout: Long = 10_000L,
        validationTimeout: Long = 5_000L,
        autoCommit: Boolean = true,
        connectionInitSql: String? = null
    ): HikariDataSource {
        MatrixJdbcCoreLibraries.ensureLoaded()
        MatrixSqliteRuntimeDriver.ensureLoaded()
        file.parentFile?.mkdirs()
        val hikari = HikariConfig().apply {
            this.poolName = poolName
            driverClassName = "org.sqlite.JDBC"
            jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
            this.maximumPoolSize = maximumPoolSize.coerceAtLeast(1)
            this.minimumIdle = minimumIdle.coerceIn(1, this.maximumPoolSize)
            this.connectionTimeout = connectionTimeout
            this.validationTimeout = validationTimeout
            this.isAutoCommit = autoCommit
            if (!connectionInitSql.isNullOrBlank()) {
                this.connectionInitSql = connectionInitSql
            }
            initializationFailTimeout = -1L
        }
        return HikariDataSource(hikari)
    }

    fun mysql(
        poolName: String,
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String,
        params: String = "",
        maximumPoolSize: Int = 4,
        minimumIdle: Int = 1,
        connectionTimeout: Long = 10_000L,
        validationTimeout: Long = 5_000L,
        autoCommit: Boolean = true
    ): HikariDataSource {
        MatrixJdbcCoreLibraries.ensureLoaded()
        MatrixMySqlRuntimeDriver.ensureLoaded()
        val hikari = HikariConfig().apply {
            this.poolName = poolName
            driverClassName = "com.mysql.cj.jdbc.Driver"
            jdbcUrl = buildMysqlJdbcUrl(host, port, database, params)
            this.username = username
            this.password = password
            this.maximumPoolSize = maximumPoolSize.coerceAtLeast(1)
            this.minimumIdle = minimumIdle.coerceIn(1, this.maximumPoolSize)
            this.connectionTimeout = connectionTimeout
            this.validationTimeout = validationTimeout
            this.isAutoCommit = autoCommit
            initializationFailTimeout = -1L
        }
        return HikariDataSource(hikari)
    }

    private fun buildMysqlJdbcUrl(
        host: String,
        port: Int,
        database: String,
        params: String
    ): String {
        return buildString {
            append("jdbc:mysql://")
            append(host)
            append(":")
            append(port)
            append("/")
            append(database)
            if (params.isNotBlank()) {
                append("?")
                append(params)
            }
        }
    }
}
