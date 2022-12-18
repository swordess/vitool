/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.sql

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.create.table.CreateTable
import java.util.Date

data class SchemaDesc(val tables: List<TableDesc>, val timestamp: Date)

typealias Options = List<String>

data class TableDesc(
    val name: String,
    val columns: List<ColumnDesc>,
    val indexes: List<IndexDesc>,
    val options: Options,
    val rawSql: String,
)

private interface NamedSqlSnippet {
    val name: String
    val rawSql: String
}

data class ColumnDesc(
    override val name: String,
    val type: String,
    val specs: List<String>,
    override val rawSql: String
) : NamedSqlSnippet

data class IndexDesc(
    override val name: String,
    val type: String,
    val specs: List<String>,
    override val rawSql: String
) : NamedSqlSnippet

private const val MYSQL_INDEX_TYPE_PK = "PRIMARY KEY"

/**
 * When type is 'PRIMARY KEY', `net.sf.jsqlparser.statement.create.table.Index#getName()` is null, so we need a replacement.
 */
private const val MYSQL_INDEX_NAME_PK = "__PK__"

data class SchemaDiff(val tables: List<TableMissingDiff>, val insideTables: List<TableDetailDiff>, val timestamp: Date)

data class TableMissingDiff(val left: TableDdl?, val right: TableDdl?)

data class TableDdl(val name: String, val sql: String)

data class TableDetailDiff(
    val name: String,
    val columns: List<StringDiff>,
    val indexes: List<StringDiff>,
    val option: StringDiff?
)

data class StringDiff(val left: String?, val right: String?)

enum class SqlFeature {
    comment,
    index_storage_type,
    row_format
}

fun parseCreateTable(sql: String): TableDesc = with(CCJSqlParserUtil.parse(sql) as CreateTable) {
    val columns = columnDefinitions.map {
        ColumnDesc(
            it.columnName,
            it.colDataType.toString(),
            it.columnSpecs,
            it.toString()
        )
    }

    val indexes = indexes?.map {
        IndexDesc(
            if (it.type == MYSQL_INDEX_TYPE_PK) MYSQL_INDEX_NAME_PK else it.name,
            it.type,
            it.indexSpec,
            it.toString()
        )
    } ?: emptyList()

    val options = tableOptionsStrings

    TableDesc(table.name, columns, indexes, options, sql)
}

fun diff(left: SchemaDesc, right: SchemaDesc, ignores: Set<SqlFeature>): SchemaDiff {
    /* ***** table missing diff ***** */

    val leftTableNames = left.tables.map { it.name }
    val rightTableNames = right.tables.map { it.name }

    val missingTables = mutableListOf<TableMissingDiff>().apply {
        // in left, but not in right
        addAll((leftTableNames - rightTableNames.toSet()).map { leftTableName ->
            val leftTable = left.tables.first { it.name == leftTableName }
            TableMissingDiff(left = TableDdl(leftTable.name, leftTable.rawSql), right = null)
        })

        // in right, but not in left
        addAll((rightTableNames - leftTableNames.toSet()).map { rightTableName ->
            val rightTable = left.tables.first { it.name == rightTableName }
            TableMissingDiff(left = null, right = TableDdl(rightTable.name, rightTable.rawSql))
        })
    }


    /* ***** table detail diff ***** */

    val tableDetailDiffs = mutableListOf<TableDetailDiff>()

    leftTableNames.intersect(rightTableNames.toSet()).forEach { tableName ->
        val leftTable = left.tables.first { it.name == tableName }
        val rightTable = right.tables.first { it.name == tableName }

        val columnDiffs = computeSnippetDiffs(
            leftTable.columns.associate { it.name to NamedSqlSnippetPair(it.ignore(ignores).copy(rawSql = ""), it) },
            rightTable.columns.associate { it.name to NamedSqlSnippetPair(it.ignore(ignores).copy(rawSql = ""), it) }
        )

        val indexDiffs = computeSnippetDiffs(
            leftTable.indexes.associate { it.name to NamedSqlSnippetPair(it.ignore(ignores).copy(rawSql = ""), it) },
            rightTable.indexes.associate { it.name to NamedSqlSnippetPair(it.ignore(ignores).copy(rawSql = ""), it) }
        )

        val leftOptions = leftTable.options.ignore(ignores)
        val rightOptions = rightTable.options.ignore(ignores)
        val optionDiff = if (leftOptions != rightOptions) StringDiff(
            leftTable.options.joinToString(" "),
            rightTable.options.joinToString(" ")
        ) else null

        if (columnDiffs.isNotEmpty() || indexDiffs.isNotEmpty() || optionDiff != null) {
            tableDetailDiffs.add(TableDetailDiff(tableName, columnDiffs, indexDiffs, optionDiff))
        }
    }

    return SchemaDiff(missingTables, tableDetailDiffs, Date())
}

private fun ColumnDesc.ignore(feature: SqlFeature): ColumnDesc = when(feature) {
    // snippet: COMMENT '主键'
    SqlFeature.comment -> copy(specs = specs.exclude(fromElement = "COMMENT", count = 2))
    else -> copy()
}

private fun IndexDesc.ignore(feature: SqlFeature): IndexDesc = when(feature) {
    // snippet: COMMENT 'XXX索引'
    SqlFeature.comment -> copy(specs = specs.exclude(fromElement = "COMMENT", count = 2))
    // snippet: USING BTREE
    SqlFeature.index_storage_type -> copy(specs = specs.exclude(fromElement = "USING", count = 2))
    else -> copy()
}

private fun Options.ignore(feature: SqlFeature): Options = when (feature) {
    // snippet: comment '日志表'
    SqlFeature.comment -> exclude(fromElement = "COMMENT", count = 2)
    // snippet : ROW_FORMAT = DYNAMIC
    SqlFeature.row_format -> exclude(fromElement = "ROW_FORMAT", count = 3)
    else -> this
}

private fun ColumnDesc.ignore(features: Set<SqlFeature>): ColumnDesc {
    var result = this
    features.forEach { result = result.ignore(it) }
    return result
}

private fun IndexDesc.ignore(features: Set<SqlFeature>): IndexDesc {
    var result = this
    features.forEach { result = result.ignore(it) }
    return result
}

private fun Options.ignore(features: Set<SqlFeature>): Options {
    var result = this
    features.forEach { result = result.ignore(it) }
    return result
}

private fun List<String>.exclude(fromElement: String, count: Int): List<String> {
    val indexOfEl = indexOf(fromElement)
    return if (indexOfEl != -1) filterIndexed { index, _ -> index !in (indexOfEl until indexOfEl + count) } else this
}

private data class NamedSqlSnippetPair(
    /**
     * With `rawSql` set to "", used for computing the difference.
     */
    val value: NamedSqlSnippet,

    val original: NamedSqlSnippet
)

private fun Map<String, NamedSqlSnippetPair>.rawSql(name: String): String? = get(name)?.original?.rawSql

private fun computeSnippetDiffs(
    left: Map<String, NamedSqlSnippetPair>,
    right: Map<String, NamedSqlSnippetPair>
): List<StringDiff> {
    val leftValues = left.values.map { it.value }
    val rightValues = right.values.map { it.value }

    val commonNames = left.keys.intersect(right.keys)

    return mutableListOf<StringDiff>().apply {
        // missing - in left, but not in right
        addAll((leftValues.filter { it.name !in commonNames } - rightValues.toSet()).map {
            StringDiff(left = left.rawSql(it.name)!!, right = null)
        })

        // missing - in right, but not in left
        addAll((rightValues.filter { it.name !in commonNames } - leftValues.toSet()).map {
            StringDiff(left = null, right = right.rawSql(it.name)!!)
        })

        // detail
        val commonLeftSnippets = leftValues.filter { it.name in commonNames }
        val commonRightSnippets = rightValues.filter { it.name in commonNames }
        addAll((commonLeftSnippets - commonRightSnippets.toSet()).map { leftSnippet ->
            val rightSnippet = commonRightSnippets.first { it.name == leftSnippet.name }
            StringDiff(left = left.rawSql(leftSnippet.name), right = right.rawSql(rightSnippet.name))
        })
    }
}
