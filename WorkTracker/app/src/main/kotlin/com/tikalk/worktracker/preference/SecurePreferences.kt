package com.tikalk.worktracker.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import com.tikalk.security.CipherHelper
import com.tikalk.security.SimpleCipherHelper
import java.util.*

/**
 * Secured preferences that encrypt/decrypt the keys and values.
 * @author moshe on 2018/07/17.
 */
@SuppressLint("HardwareIds")
class SecurePreferences(context: Context, name: String, mode: Int) : SharedPreferences {

    private val delegate: SharedPreferences = context.getSharedPreferences(name, mode)
    private val cipher: CipherHelper

    init {
        val prefs = context.getSharedPreferences(KEYS_PREFS_NAME, Context.MODE_PRIVATE)
        val privateKey = prefs.getString(PREF_KEY, UUID.randomUUID().toString())
        if (!prefs.contains(PREF_KEY)) {
            prefs.edit().putString(PREF_KEY, privateKey).apply()
        }
        val salt = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        cipher = SimpleCipherHelper(privateKey, salt)
    }

    override fun contains(key: String): Boolean {
        return delegate.contains(hashKey(key))
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor()
    }

    override fun getAll(): Map<String, *> {
        return delegate.all
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return decryptBoolean(key, getCrypticString(key), defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return decryptFloat(key, getCrypticString(key), defValue)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return decryptInt(key, getCrypticString(key), defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return decryptLong(key, getCrypticString(key), defValue)
    }

    override fun getString(key: String, defValue: String?): String? {
        return decryptString(key, getCrypticString(key), defValue)
    }

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? {
        return decryptStringSet(key, getCrypticStringSet(key), defValue)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        return delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        return delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun getCrypticString(key: String): String? {
        return delegate.getString(hashKey(key), null)
    }

    private fun getCrypticStringSet(key: String): Set<String>? {
        return delegate.getStringSet(hashKey(key), null)
    }

    private fun hashKey(key: String): String {
        return cipher.hash(key)
    }

    private fun encrypt(key: String, clear: String?): String {
        return cipher.encrypt(clear ?: "", key)
    }

    private fun decrypt(key: String, cryptic: String): String {
        return cipher.decrypt(cryptic, key)
    }

    private fun decryptBoolean(key: String, cryptic: String?, defValue: Boolean): Boolean {
        return if (cryptic == null) defValue else decrypt(key, cryptic).toBoolean()
    }

    private fun decryptFloat(key: String, cryptic: String?, defValue: Float): Float {
        return if (cryptic == null) defValue else decrypt(key, cryptic).toFloat()
    }

    private fun decryptInt(key: String, cryptic: String?, defValue: Int): Int {
        return if (cryptic == null) defValue else decrypt(key, cryptic).toInt()
    }

    private fun decryptLong(key: String, cryptic: String?, defValue: Long): Long {
        return if (cryptic == null) defValue else decrypt(key, cryptic).toLong()
    }

    private fun decryptString(key: String, cryptic: String?, defValue: String?): String? {
        return if (cryptic == null) defValue else decrypt(key, cryptic)
    }

    private fun decryptStringSet(key: String, cryptics: Set<String>?, defValue: Set<String>?): Set<String>? {
        return if (cryptics == null) {
            defValue
        } else {
            val result = LinkedHashSet<String>()
            for (cryptic in cryptics) {
                result.add(decrypt(key, cryptic))
            }
            return result
        }
    }

    companion object {
        private const val KEYS_PREFS_NAME = "secpref"
        private const val PREF_KEY = "private_key"

        /**
         * Gets a [SecurePreferences] instance that points to the default file that is used by
         * the preference framework in the given context.
         *
         * @param context The context of the preferences whose values are wanted.
         * @return A [SecurePreferences] instance that can be used to retrieve and listen
         * to values of the preferences.
         */
        fun getDefaultSharedPreferences(context: Context): SecurePreferences {
            return getSharedPreferences(context, getDefaultSharedPreferencesName(context),
                    getDefaultSharedPreferencesMode())
        }

        fun getSharedPreferences(context: Context, name: String, mode: Int): SecurePreferences {
            return SecurePreferences(context, name, mode)
        }

        /**
         * Returns the name used for storing default shared preferences.
         *
         * @param context The context of the preferences whose values are wanted.
         * @see #getDefaultSharedPreferences(Context)
         * @see Context#getSharedPreferencesPath(String)
         */
        fun getDefaultSharedPreferencesName(context: Context): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PreferenceManager.getDefaultSharedPreferencesName(context)
            } else {
                context.packageName + "_preferences"
            }
        }

        fun getDefaultSharedPreferencesMode(): Int {
            return Context.MODE_PRIVATE
        }
    }

    private inner class Editor : SharedPreferences.Editor {

        private val delegate: SharedPreferences.Editor = this@SecurePreferences.delegate.edit()

        override fun apply() {
            delegate.apply()
        }

        override fun clear(): SharedPreferences.Editor {
            delegate.clear()
            return this
        }

        override fun commit(): Boolean {
            return delegate.commit()
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            delegate.putString(hashKey(key), encrypt(key, value.toString()))
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            delegate.putString(hashKey(key), encrypt(key, value.toString()))
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            delegate.putString(hashKey(key), encrypt(key, value.toString()))
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            delegate.putString(hashKey(key), encrypt(key, value.toString()))
            return this
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            delegate.putString(hashKey(key), encrypt(key, value))
            return this
        }

        override fun putStringSet(key: String, value: Set<String>): SharedPreferences.Editor {
            val items = LinkedHashSet<String>()
            for (item in value) {
                items.add(encrypt(key, item))
            }
            delegate.putStringSet(hashKey(key), items)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            delegate.remove(hashKey(key))
            return this
        }
    }
}