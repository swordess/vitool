/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.shell.Availability
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellMethodAvailability
import org.springframework.shell.standard.ShellOption
import org.springframework.shell.table.ArrayTableModel
import org.springframework.shell.table.BorderStyle
import org.springframework.shell.table.TableBuilder
import org.springframework.shell.table.TableModel
import org.swordess.common.vitool.cmd.customize.Quit
import org.swordess.common.vitool.ext.shell.AbstractShellComponent
import org.swordess.common.vitool.ext.shell.toEnums
import org.swordess.common.vitool.ext.shell.toOssFileProperties
import org.swordess.common.vitool.ext.sql.*
import org.swordess.common.vitool.ext.storage.*
import java.lang.RuntimeException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*
import kotlin.properties.Delegates

private const val ENV_VI_DB_URL = "VI_DB_URL"
private const val ENV_VI_DB_USERNAME = "VI_DB_USERNAME"
private const val ENV_VI_DB_PASSWORD = "VI_DB_PASSWORD"

private const val ENV_VI_OSS_ACCESS_ID = "VI_OSS_ACCESS_ID"
private const val ENV_VI_OSS_ACCESS_SECRET = "VI_OSS_ACCESS_SECRET"

private const val CONNECTION_NAME_DEFAULT = "default"

private const val LOCATION_CONSOLE = "console"
private const val LOCATION_PREFIX_OSS = "oss://"

private data class ConnectionProperties(
    var url: String? = null,
    var username: String? = null,
    var password: String? = null
)

private data class DbConnection(
    val name: String,
    val props: ConnectionProperties,
    val conn: Connection,
    val create: DSLContext
)

@ShellComponent
class DatabaseCommands : AbstractShellComponent() {

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    private val namedConnections: MutableMap<String, DbConnection> = mutableMapOf()

    private var activeConnection: DbConnection? by Delegates.observable(null) { _, old, new ->
        // change
        if (old != new) {
            if (old == null) {
                // null -> connection
                println("Connection switched from (none) to '${new!!.name}' .")

            } else if (new == null) {
                // connection -> null
                println("Connection switched from '${old.name}' to (none) .")

            } else {
                // connection a -> connection b
                println("Connection switched from '${old.name}' to '${new.name}' .")
            }
        }
    }

    init {
        Quit.onExit {
            try {
                // `dbClose` modified the `namedConnections`, so make a copy to avoid ConcurrentModificationException
                namedConnections.keys.toList().forEach {
                    dbClose(it)
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }


    /* ***********************************/
    /* ***** Connection Management ***** */
    /* ***********************************/

    @ShellMethod(key = ["db connect"], value = "Establish a database connection.")
    fun dbConnect(
        @ShellOption(
            help = "jdbc url, read from the environment variable `$ENV_VI_DB_URL` if not specified",
            defaultValue = ShellOption.NULL
        ) url: String?,

        @ShellOption(
            help = "username, read from the environment variable `$ENV_VI_DB_USERNAME` if not specified",
            defaultValue = ShellOption.NULL
        ) username: String?,

        @ShellOption(
            help = "password, read from the environment variable `$ENV_VI_DB_PASSWORD` if not specified",
            defaultValue = ShellOption.NULL
        ) password: String?,

        @ShellOption(help = "connection name", defaultValue = CONNECTION_NAME_DEFAULT) name: String
    ) {
        if (namedConnections.containsKey(name)) {
            println("Connection named '$name' already exists, please close it first.")
            return
        }

        val urlOption = url.orEnv(ENV_VI_DB_URL).must("`url` cannot be inferred")
        val usernameOption = username.orEnv(ENV_VI_DB_USERNAME).must("`username` cannot be inferred")
        val passwordOption = password.orEnv(ENV_VI_DB_PASSWORD).orInput("Enter password:")
            .must("`password` cannot be inferred")

        val props = ConnectionProperties(urlOption, usernameOption, passwordOption)
        val conn = DriverManager.getConnection(props.url, props.username, props.password)
        val create = DSL.using(conn)

        println("Connection[name='$name'] has been established.")

        with(DbConnection(name, props, conn, create)) {
            namedConnections[this.name] = this
            activeConnection = this
        }
    }

    @ShellMethod(key = ["db close"], value = "Close the current active connection.")
    fun dbClose(
        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL
        ) name: String?
    ) {
        val connection = mustConnection(name ?: activeConnection!!.name)
        try {
            with(connection) {
                conn.close()
                println("Connection[name='${this.name}'] has been closed.")
            }
        } finally {
            namedConnections.remove(connection.name)
            activeConnection = null
        }
    }

    @ShellMethod(key = ["db reconnect"], value = "Re-establish the connection using the last successful properties.")
    fun dbReconnect(
        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL
        ) name: String?
    ) {
        with(mustConnection(name ?: activeConnection!!.name)) {
            dbClose(this.name)
            dbConnect(props.url, props.username, props.password, this.name)
        }
    }

    @ShellMethod(key = ["db connections"], value = "List available connections.")
    fun dbConnections(): String {
        if (namedConnections.isEmpty()) {
            return "(No connections.)"
        }

        return namedConnections.values.joinToString(separator = "\n") {
            """
                ${if (activeConnection == it) "* " else ""}${it.name}:
                    url: ${it.props.url}
                    username: ${it.props.username}""".trimIndent()
        }
    }

    @ShellMethod(key = ["db switch"], value = "Switch active connection to specified one.")
    fun dbSwitch(@ShellOption(help = "connection-name") name: String) {
        if (name == activeConnection?.name) {
            println("(Nothing happened.)")
            return
        }

        activeConnection = mustConnection(name)
    }


    /* *************************/
    /* ***** SQL Actions ***** */
    /* *************************/

    @ShellMethod(key = ["db query"], value = "Execute select statements using the current active connection.")
    fun dbQuery(
        sql: String,

        @ShellOption(help = "the output format when displaying the result", defaultValue = "table")
        format: QueryFormat,

        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL
        ) name: String? = null
    ): Any? {
        val conn = mustConnection(name ?: activeConnection!!.name)
        println("(Using connection[name='${conn.name}'] ...)\n")

        conn.create.resultQuery(sql).use { query ->
            val result = query.fetchMaps()

            if (QueryFormat.json == format) {
                val output = StringBuilder()
                for (i in result.indices) {
                    output.append("[").append(i).append("] = ").append(gson.toJson(result[i])).append("\n")
                }
                output.append(result.size).append(" row(s) returned")
                return output

            } else if (QueryFormat.table == format) {
                println(result.size.toString() + " row(s) returned")

                if (result.isNotEmpty()) {
                    val rowCount = result.size
                    val colCount = result[0].size

                    // one more row for header
                    val data = Array(rowCount + 1) {
                        arrayOfNulls<Any>(colCount)
                    }

                    // set header names
                    data[0] = result[0].keys.toTypedArray()

                    for (i in result.indices) {
                        val row = result[i]
                        data[i + 1] = row.values.toTypedArray()
                    }

                    val model: TableModel = ArrayTableModel(data)
                    return TableBuilder(model).addHeaderAndVerticalsBorders(BorderStyle.oldschool).build()
                }
            }
        }

        return null
    }

    enum class QueryFormat {
        json, table
    }

    @ShellMethod(key = ["db command"], value = "Execute DML statements using the current active connection.")
    fun dbCommand(
        sql: String,
        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL
        ) name: String?
    ) {
        val conn = mustConnection(name ?: activeConnection!!.name)
        println("(Using connection[name='${conn.name}'] ...)\n")

        conn.create.query(sql).use { query ->
            val affectedRows = query.execute()
            println("$affectedRows row(s) affected")
        }
    }


    /* ****************************/
    /* ***** Schema Actions ***** */
    /* ****************************/

    @ShellMethod(key = ["db schema dump"], value = "Dump all tables as json descriptions.")
    fun dbSchemaDump(
        @ShellOption(
            help = "location for saving the descriptions. Possible values are: '${LOCATION_CONSOLE}' | '${LOCATION_PREFIX_OSS}...' | <file> ",
            defaultValue = LOCATION_CONSOLE
        ) to: String,
        @ShellOption(help = "use pretty json or not", defaultValue = "false") pretty: Boolean,
        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL
        ) name: String?
    ) {
        val conn = mustConnection(name ?: activeConnection!!.name)
        println("(Using connection[name='${conn.name}'] ...)\n")

        val schemaDesc = querySchemaDescription(conn)
        writeJson(schemaDesc, to, pretty) {
            if (schemaDesc.tables.size > 1) {
                println("${schemaDesc.tables.size} table descriptions have be written to \"$it\" .")
            } else {
                println("${schemaDesc.tables.size} table description has be written to \"$it\" .")
            }
        }
    }

    private fun querySchemaDescription(connection: DbConnection): SchemaDesc {
        val tableDescs = mutableListOf<TableDesc>()

        with(connection.create) {
            if (configuration().dialect() != SQLDialect.MYSQL) {
                throw RuntimeException("Only mysql is supported.")
            }

            val tableNames = resultQuery("show tables").use { it.fetchInto(String::class.java) }

            tableNames.forEach { tableName ->
                // Two columns will be returned: `Table`, `Create Table`. Use `fetchOne` for simplicity.
                val createTableSql: String? =
                    resultQuery("show create table $tableName").use { it.fetchOne(1, String::class.java) }

                createTableSql?.let {
                    tableDescs.add(parseCreateTable(it))
                }
            }
        }

        return SchemaDesc(tableDescs, Date())
    }

    @ShellMethod(
        key = ["db schema diff"],
        value = "Compute differences of two table descriptions."
    )
    fun dbSchemaDiff(
        @ShellOption(help = "location for getting the left side descriptions. Possible values are: <connection_name> | '${LOCATION_PREFIX_OSS}...' | <file>") left: String,
        @ShellOption(help = "location for getting the right side descriptions. Possible values are: <connection_name> | '${LOCATION_PREFIX_OSS}...' | <file>") right: String,
        @ShellOption(
            help = "location for saving the descriptions. Possible values are: '${LOCATION_CONSOLE}' | '${LOCATION_PREFIX_OSS}...' | <file>",
            defaultValue = LOCATION_CONSOLE
        ) to: String,
        @ShellOption(help = "use pretty json or not", defaultValue = "true") pretty: Boolean,
        @ShellOption(
            help = "ignore sql features, comma(',') separated. Possible values are: comment, index_storage_type, row_format",
            defaultValue = ShellOption.NULL
        ) ignore: String?
    ) {
        val ignores = ignore?.toEnums<SqlFeature>() ?: emptySet()

        val leftDesc = left.toSourceDescriptionProvider {
            println("Left side descriptions have been loaded from \"$it\" .")
        }.invoke()
        val rightDesc = right.toSourceDescriptionProvider {
            println("Right side descriptions have been loaded from \"$it\" .")
        }.invoke()

        val schemaDiff = diff(leftDesc, rightDesc, ignores)

        if (schemaDiff.tables.isEmpty() && schemaDiff.insideTables.isEmpty()) {
            println("(No differences.)")
            return
        }

        writeJson(schemaDiff, to, pretty) {
            println("Differences have been written to \"$it\" .")
        }
    }

    private fun writeJson(data: Any, to: String, pretty: Boolean, callback: (Any?) -> Unit) {
        val gson = GsonBuilder().apply {
            disableHtmlEscaping()
            if (pretty) {
                setPrettyPrinting()
            }
        }.create()

        to.toDestDescriptionStorage().write(gson.toJson(data).toByteArray(), callback)
    }

    private fun String.toDestDescriptionStorage(): WriteableData<out Any> =
        if (this == LOCATION_CONSOLE) {
            ConsoleData
        } else if (startsWith(LOCATION_PREFIX_OSS)) {
            toOssData()
        } else {
            FileData(this)
        }

    private fun String.toSourceDescriptionProvider(callback: (String) -> Unit): () -> SchemaDesc {
        if (this in namedConnections) {
            val connection = namedConnections[this]!!
            return {
                val result = querySchemaDescription(connection)
                callback.invoke("connection[name='${connection.name}']")
                result
            }

        } else if (startsWith(LOCATION_PREFIX_OSS)) {
            val data = toOssData()
            return {
                gson.fromJson(JsonReader(data.read(callback).inputStream().reader()), SchemaDesc::class.java)
            }

        } else {
            val data = FileData(this)
            return {
                gson.fromJson(JsonReader(data.read(callback).inputStream().reader()), SchemaDesc::class.java)
            }
        }
    }

    private fun String.toOssData(): OssData {
        val props = toOssFileProperties()

        props.accessId = props.accessId
            .orEnv(ENV_VI_OSS_ACCESS_ID)
            .orInput("Enter OSS access id:", mask = false)
            .must("OSS access id cannot be inferred")

        props.accessSecret = props.accessSecret
            .orEnv(ENV_VI_OSS_ACCESS_SECRET)
            .orInput("Enter OSS access secret:")
            .must("OSS access id cannot be inferred")

        return OssData(props)
    }

    private fun mustConnection(name: String): DbConnection =
        namedConnections[name]
            ?: throw RuntimeException("there is no connection named '$name', possible values are: ${namedConnections.keys}")

    @ShellMethodAvailability("db query", "db command", "db close", "db reconnect", "db schema dump")
    fun availabilityCheck(): Availability =
        if (activeConnection != null) Availability.available() else Availability.unavailable("the connection is not established")

}