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
        // Blocks unsafe SQL tricks in selection, projection, and sortOrder
        queryBuilder.setStrict(true)

        var safeSelection = selection
        var safeSelectionArgs = selectionArgs

        when (sURIMatcher.match(uri)) {
            ACCOUNTS_ID -> {
                // Make sure the last part of the URI is a valid number
                val accountId = ContentUris.parseId(uri)

                // Use '?' placeholder instead of joining strings
                val idCondition = "$COLUMN_ID = ?"
                safeSelection = if (selection.isNullOrBlank()) {
                    idCondition
                } else {
                    "($selection) AND $idCondition"
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
            projection,
            safeSelection,
            safeSelectionArgs,
            null,
            null,
            sortOrder
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

    companion object {
        private const val AUTHORITY = "com.cx.goatlin.accounts"
        private const val ACCOUNTS_TABLE = "Accounts"
        // Check DatabaseHelper.kt to confirm if this column is "_id" or "id"
        private const val COLUMN_ID = "_id"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$ACCOUNTS_TABLE")
        private const val DATABASE_NAME = "data"
    }
}