/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.shell.Availability
import org.springframework.shell.CompletionContext
import org.springframework.shell.CompletionProposal
import org.springframework.shell.command.CommandRegistration
import org.springframework.shell.standard.EnumValueProvider
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellMethodAvailability
import org.springframework.shell.standard.ShellOption
import org.springframework.shell.standard.ValueProvider
import org.springframework.shell.table.ArrayTableModel
import org.springframework.shell.table.BorderStyle
import org.springframework.shell.table.TableBuilder
import org.springframework.shell.table.TableModel
import org.swordess.common.vitool.cmd.customize.Quit
import org.swordess.common.vitool.ext.shell.AbstractShellComponent
import org.swordess.common.vitool.ext.shell.toOssFileProperties
import org.swordess.common.vitool.ext.slf4j.CmdLogger
import org.swordess.common.vitool.ext.sql.*
import org.swordess.common.vitool.ext.storage.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
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

    private val json = Json { prettyPrint = true }

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
            // `dbClose` modified the `namedConnections`, so make a copy to avoid ConcurrentModificationException
            namedConnections.keys.toList().forEach {
                try {
                    dbClose(it)
                } catch (e: SQLException) {
                    CmdLogger.db.warn("failed to close db connection", e)
                }
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
        val conn: Connection = DriverManager.getConnection(props.url, props.username, props.password)
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
            defaultValue = ShellOption.NULL,
            valueProvider = ConnectionNameProvider::class
        ) name: String?
    ) {
        val connection = mustConnection(name ?: activeConnection!!.name)
        try {
            connection.conn.use {
                // intended to safely close the db connection
            }
            println("Connection[name='${connection.name}'] has been closed.")
        } finally {
            namedConnections.remove(connection.name)
            activeConnection = null
        }
    }

    @ShellMethod(key = ["db reconnect"], value = "Re-establish the connection using the last successful properties.")
    fun dbReconnect(
        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL,
            valueProvider = ConnectionNameProvider::class
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
    fun dbSwitch(@ShellOption(help = "connection-name", valueProvider = ConnectionNameProvider::class) name: String) {
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

        @ShellOption(
            help = "the output format when displaying the result",
            defaultValue = "table",
            valueProvider = EnumValueProvider::class
        )
        format: QueryFormat,

        @ShellOption(
            help = "connection name, default to current active connection's name",
            defaultValue = ShellOption.NULL,
            valueProvider = ConnectionNameProvider::class
        ) name: String? = null
    ): Any? {
        val conn = mustConnection(name ?: activeConnection!!.name)
        println("(Using connection[name='${conn.name}'] ...)\n")

        conn.create.resultQuery(sql).use { query ->
            val result = query.fetchMaps()

            if (QueryFormat.json == format) {
                val output = StringBuilder()
                for (i in result.indices) {
                    output.append("[").append(i).append("] = ").append(json.encodeToString(result[i])).append("\n")
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

                    for ((i, row) in result.withIndex()) {
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
            defaultValue = ShellOption.NULL,
            valueProvider = ConnectionNameProvider::class
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
            defaultValue = ShellOption.NULL,
            valueProvider = ConnectionNameProvider::class
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
                throw UnsupportedOperationException("Only mysql is supported.")
            }

            val tableNames = resultQuery("show tables").use { it.fetchInto(String::class.java) }

            tableNames.forEach { tableName ->
                // Two columns will be returned: `Table`, `Create Table`. Use `fetchOne` for simplicity.
                val createTableSql: String? =
                    resultQuery("show create table $tableName").use { it.fetchOne(1, String::class.java) }

                createTableSql?.also {
                    tableDescs.add(parseCreateTable(it))
                } ?: CmdLogger.db.warn("cannot fetch DDL for table `$tableName`")
            }
        }

        return SchemaDesc(tableDescs, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    }

    private fun dbSchemaDiff(left: String, right: String, to: String, pretty: Boolean, ignores: Set<SqlFeature>) {
        val schemaDiff = runBlocking {
            val leftDesc = async {
                left.toSourceDescriptionProvider {
                    println("Left side descriptions have been loaded from \"$it\" .")
                }.invoke()
            }
            val rightDesc = async {
                right.toSourceDescriptionProvider {
                    println("Right side descriptions have been loaded from \"$it\" .")
                }.invoke()
            }
            diff(leftDesc.await(), rightDesc.await(), ignores)
        }

        if (schemaDiff.tables.isEmpty() && schemaDiff.insideTables.isEmpty()) {
            println("(No differences.)")
            return
        }

        writeJson(schemaDiff, to, pretty) {
            println("Differences have been written to \"$it\" .")
        }
    }

    @Bean
    fun dbSchemaDiffRegistration(): CommandRegistration {
        return CommandRegistration.builder()
            .group("Database Commands")
            .command("db schema diff")
            .description("Compute differences of two table descriptions.")
            .withOption()
                .longNames("left")
                .description("location for getting the left side descriptions. Possible values are: <connection_name> | '${LOCATION_PREFIX_OSS}...' | <file>")
                .required()
                .and()
            .withOption()
                .longNames("right")
                .description("location for getting the right side descriptions. Possible values are: <connection_name> | '${LOCATION_PREFIX_OSS}...' | <file>")
                .required()
                .and()
            .withOption()
                .longNames("to")
                .description("location for saving the descriptions. Possible values are: '${LOCATION_CONSOLE}' | '${LOCATION_PREFIX_OSS}...' | <file>")
                .defaultValue(LOCATION_CONSOLE)
                .and()
            .withOption()
                .longNames("pretty")
                .description("use pretty json or not")
                .defaultValue("true")
                .type(Boolean::class.java)
                .and()
            .withOption()
                .longNames("ignore")
                .description("ignore sql features. Possible values are: ${enumValues<SqlFeature>().joinToString(", ")}")
                .required(false)
                .type(SqlFeature::class.java)
                .arity(1, enumValues<SqlFeature>().size)
                .completion(EnumValueProvider()::complete)
                .and()
            .withTarget()
                .consumer { ctx ->
                    val ignore = ctx.parserResults.results()
                        .filter { "ignore" in it.option().longNames }
                        .map { it.value() as SqlFeature }
                    dbSchemaDiff(
                        ctx.getOptionValue("left"),
                        ctx.getOptionValue("right"),
                        ctx.getOptionValue("to"),
                        ctx.getOptionValue("pretty"),
                        ignore.toSet())
                }
                .and()
            .build()
    }

    private inline fun <reified T> writeJson(data: T, to: String, pretty: Boolean, noinline callback: (Any) -> Unit) {
        val json = Json { prettyPrint = pretty }
        to.toDestDescriptionStorage().write(json.encodeToString(data).toByteArray(), callback)
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
                json.decodeFromString(String(data.read(callback)))
            }

        } else {
            val data = FileData(this)
            return {
                json.decodeFromString(String(data.read(callback)))
            }
        }
    }

    private fun String.toOssData(): OssData {
        var props = toOssFileProperties()

        props = props.copy(
            accessId = props.accessId
                .orEnv(ENV_VI_OSS_ACCESS_ID)
                .orInput("Enter OSS access id:", mask = false)
                .must("OSS access id cannot be inferred")
        )

        props = props.copy(
            accessSecret = props.accessSecret
                .orEnv(ENV_VI_OSS_ACCESS_SECRET)
                .orInput("Enter OSS access secret:")
                .must("OSS access secret cannot be inferred")
        )

        return OssData(props)
    }

    private fun mustConnection(name: String): DbConnection =
        namedConnections[name]
            ?: throw RuntimeException("there is no connection named '$name', possible values are: ${namedConnections.keys}")


    /* *****************************/
    /* ***** Value Providers ***** */
    /* *****************************/

    inner class ConnectionNameProvider : ValueProvider {
        override fun complete(completionContext: CompletionContext): MutableList<CompletionProposal> =
            namedConnections.keys.map { CompletionProposal(it) }.toMutableList()

    }

    @Bean
    fun connectionNameProvider() = ConnectionNameProvider()


    /* **************************/
    /* ***** Availability ***** */
    /* **************************/

    @ShellMethodAvailability("db query", "db command", "db close", "db reconnect", "db schema dump")
    fun availabilityCheck(): Availability =
        if (activeConnection != null) Availability.available()
        else {
            val msg =
                if (namedConnections.isEmpty()) "the connection is not established" else "none of connections among ${namedConnections.keys} is activated"
            Availability.unavailable(msg)
        }

}