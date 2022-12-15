/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import com.google.gson.GsonBuilder
import org.jooq.DSLContext
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
import org.swordess.common.vitool.cmd.customize.Quit.Companion.onExit
import org.swordess.common.vitool.ext.AbstractShellComponent
import java.lang.RuntimeException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

private const val ENV_VI_DB_URL = "VI_DB_URL"
private const val ENV_VI_DB_USERNAME = "VI_DB_USERNAME"
private const val ENV_VI_DB_PASSWORD = "VI_DB_PASSWORD"

private const val NAME_DEFAULT = "default"

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

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val namedConnections: MutableMap<String, DbConnection> = mutableMapOf()

    private var activeConnection: DbConnection? = null
        set(value) {
            // change:
            //   none | connection a    ->    connection b
            if (field != value && value != null) {
                if (field == null) {
                    println("Connection switched from (none) to '${value.name}' .")
                } else {
                    field?.let { old ->
                        println("Connection switched from '${old.name}' to '${value.name}' .")
                    }
                }
            }

            field = value
        }

    override fun afterPropertiesSet() {
        super.afterPropertiesSet()
        onExit {
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

        @ShellOption(help = "connection name", defaultValue = NAME_DEFAULT) name: String
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
        try {
            with(mustConnection(name ?: activeConnection!!.name)) {
                conn.close()
                println("Connection[name='${this.name}'] has been closed.")
            }
        } finally {
            namedConnections.remove(name)
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




    private fun mustConnection(name: String): DbConnection =
        namedConnections[name]
            ?: throw RuntimeException("there is no connection named '$name', possible values are: ${namedConnections.keys}")

    @ShellMethodAvailability("db query", "db command", "db close", "db reconnect")
    fun availabilityCheck(): Availability =
        if (activeConnection != null) Availability.available() else Availability.unavailable("the connection is not established")

}