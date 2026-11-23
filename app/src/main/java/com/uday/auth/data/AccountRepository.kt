package com.uday.auth.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.uday.auth.otp.Algorithm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the list of accounts in a single encrypted JSON file in app-private storage.
 * The list is small (tens of entries at most), so it's simply loaded once and rewritten on every change.
 *
 * Use [get] rather than the constructor: every screen has to share the same in-memory list,
 * otherwise a rename on the detail screen wouldn't show up back on the list.
 */
class AccountRepository private constructor(context: Context) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val cipher = KeystoreCipher(KEY_ALIAS)

    private val accounts: MutableList<Account> by lazy { load().toMutableList() }

    fun getAll(): List<Account> = accounts.toList()

    fun find(id: String): Account? = accounts.firstOrNull { it.id == id }

    fun contains(account: Account): Boolean = accounts.any { it.isSameKeyAs(account) }

    fun add(account: Account) {
        // Newest first, so a freshly added account is right at the top
        accounts.add(0, account)
        save()
    }

    fun update(account: Account) {
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
            save()
        }
    }

    fun remove(id: String) {
        if (accounts.removeAll { it.id == id }) save()
    }

    private fun load(): List<Account> {
        if (!file.baseFile.exists()) return emptyList()

        return try {
            val json = String(cipher.decrypt(file.readFully()), Charsets.UTF_8)
            val array = JSONArray(json)
            List(array.length()) { i -> array.getJSONObject(i).toAccount() }
        } catch (e: Exception) {
            // Most likely the keystore key is gone (e.g. data restored onto another device).
            // Move the file aside instead of overwriting it on the next save.
            Log.e(TAG, "Couldn't read saved accounts", e)
            file.baseFile.renameTo(File(file.baseFile.parentFile, "$FILE_NAME.unreadable"))
            emptyList()
        }
    }

    private fun save() {
        val array = JSONArray()
        accounts.forEach { array.put(it.toJson()) }
        val encrypted = cipher.encrypt(array.toString().toByteArray(Charsets.UTF_8))

        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun Account.toJson() = JSONObject()
        .put("id", id)
        .put("issuer", issuer)
        .put("label", label)
        .put("secret", secret)
        .put("algorithm", algorithm.name)
        .put("digits", digits)
        .put("period", period)

    private fun JSONObject.toAccount() = Account(
        id = getString("id"),
        issuer = optString("issuer"),
        label = optString("label"),
        secret = getString("secret"),
        algorithm = Algorithm.fromName(optString("algorithm")) ?: Algorithm.SHA1,
        digits = optInt("digits", 6),
        period = optInt("period", 30),
    )

    companion object {
        private const val TAG = "AccountRepository"
        private const val FILE_NAME = "accounts.enc"
        private const val KEY_ALIAS = "my_auth_accounts_key"

        @Volatile
        private var instance: AccountRepository? = null

        fun get(context: Context): AccountRepository =
            instance ?: synchronized(this) {
                instance ?: AccountRepository(context.applicationContext).also { instance = it }
            }
    }
}
