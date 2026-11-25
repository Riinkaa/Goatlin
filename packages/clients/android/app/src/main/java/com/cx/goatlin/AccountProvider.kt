package com.cx.goatlin

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import com.cx.goatlin.helpers.DatabaseHelper

class AccountProvider : ContentProvider() {

    private lateinit var database: DatabaseHelper

    private val ACCOUNTS = 1
    private val ACCOUNTS_ID = 2

    private val sURIMatcher = UriMatcher(UriMatcher.NO_MATCH)

    init {
        sURIMatcher.addURI(AUTHORITY, ACCOUNTS_TABLE, ACCOUNTS)
        sURIMatcher.addURI(AUTHORITY, "$ACCOUNTS_TABLE/#", ACCOUNTS_ID)
    }

    override fun onCreate(): Boolean {
        this.database = DatabaseHelper(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = ACCOUNTS_TABLE
        queryBuilder.setStrict(true)
        queryBuilder.setProjectionMap(PROJECTION_MAP)

        val safeProjection = sanitizeProjection(projection)
        val cleanSelection = sanitizeSelection(selection)
        val safeSortOrder = sanitizeSortOrder(sortOrder)

        var safeSelection = cleanSelection
        var safeSelectionArgs = selectionArgs

        when (sURIMatcher.match(uri)) {
            ACCOUNTS_ID -> {
                val accountId = ContentUris.parseId(uri)
                val idCondition = "$COLUMN_ID = ?"
                safeSelection = if (cleanSelection == null) {
                    idCondition
                } else {
                    "($cleanSelection) AND $idCondition"
                }
                safeSelectionArgs = (selectionArgs ?: emptyArray()) + accountId.toString()
            }
            ACCOUNTS -> {
                // No ID filter needed
            }
            else -> throw IllegalArgumentException("Unknown URI")
        }

        val cursor = queryBuilder.query(
            this.database.readableDatabase,
            safeProjection,
            safeSelection,
            safeSelectionArgs,
            null,
            null,
            safeSortOrder
        )
        cursor?.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues): Uri? {
        val uriType = sURIMatcher.match(uri)
        val sqlDB = this.database.writableDatabase

        val id: Long = when (uriType) {
            ACCOUNTS -> sqlDB.insert(ACCOUNTS_TABLE, null, values)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        context?.contentResolver?.notifyChange(uri, null)
        return Uri.parse("$ACCOUNTS_TABLE/$id")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun getType(uri: Uri): String? {
        TODO("Implement this to handle requests for the MIME type of the data at the given URI")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("Not yet implemented")
    }

    private fun sanitizeProjection(projection: Array<String>?): Array<String>? {
        if (projection == null) return null
        return projection.map { column ->
            ALLOWED_COLUMNS[column.trim().lowercase()]
                ?: throw IllegalArgumentException("Invalid projection column: $column")
        }.toTypedArray()
    }

    private fun sanitizeSelection(selection: String?): String? {
        if (selection.isNullOrBlank()) return null
        val clauseRegex = Regex(
            """^\s*([a-zA-Z0-9_]+)\s*(=|!=|<>|<|>|<=|>=|LIKE)\s*\?\s*$""",
            RegexOption.IGNORE_CASE
        )
        val clauses = selection.split(Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE))

        return clauses.joinToString(" AND ") { clause ->
            val match = clauseRegex.matchEntire(clause)
                ?: throw IllegalArgumentException("Invalid selection clause: $clause")
            val safeCol = ALLOWED_COLUMNS[match.groupValues[1].lowercase()]
                ?: throw IllegalArgumentException("Invalid selection column")
            val safeOp = ALLOWED_OPERATORS[match.groupValues[2].uppercase()]
                ?: throw IllegalArgumentException("Invalid selection operator")
            "$safeCol $safeOp ?"
        }
    }

    private fun sanitizeSortOrder(sortOrder: String?): String? {
        if (sortOrder.isNullOrBlank()) return null
        val sortRegex = Regex(
            """^\s*([a-zA-Z0-9_]+)(?:\s+(ASC|DESC))?\s*$""",
            RegexOption.IGNORE_CASE
        )
        return sortOrder.split(",").joinToString(", ") { term ->
            val match = sortRegex.matchEntire(term)
                ?: throw IllegalArgumentException("Invalid sortOrder term: $term")
            val safeCol = ALLOWED_COLUMNS[match.groupValues[1].lowercase()]
                ?: throw IllegalArgumentException("Invalid sortOrder column")
            when (match.groupValues[2].uppercase()) {
                "ASC" -> "$safeCol ASC"
                "DESC" -> "$safeCol DESC"
                "" -> safeCol
                else -> throw IllegalArgumentException("Invalid sort direction")
            }
        }
    }

    companion object {
        private const val AUTHORITY = "com.cx.goatlin.accounts"
        private const val ACCOUNTS_TABLE = "Accounts"
        private const val COLUMN_ID = "rowid"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$ACCOUNTS_TABLE")
        private const val DATABASE_NAME = "data"

        private val ALLOWED_COLUMNS = mapOf(
            "rowid" to "rowid",
            "_id" to "_id",
            "username" to "Username",
            "password" to "Password"
        )

        private val PROJECTION_MAP = mapOf(
            "rowid" to "rowid",
            "_id" to "_id",
            "Username" to "Username",
            "Password" to "Password"
        )

        private val ALLOWED_OPERATORS = mapOf(
            "=" to "=",
            "!=" to "!=",
            "<>" to "<>",
            "<" to "<",
            ">" to ">",
            "<=" to "<=",
            ">=" to ">=",
            "LIKE" to "LIKE"
        )
    }
}