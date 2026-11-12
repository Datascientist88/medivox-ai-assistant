package com.medivox.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.medivox.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BackendApi(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun get(path: String, authenticated: Boolean = true): JSONObject =
        request("GET", path, null, authenticated)

    suspend fun post(
        path: String,
        payload: JSONObject = JSONObject(),
        authenticated: Boolean = true,
    ): JSONObject = request("POST", path, payload, authenticated)

    suspend fun delete(path: String, authenticated: Boolean = true): JSONObject =
        request("DELETE", path, null, authenticated)

    private suspend fun request(
        method: String,
        path: String,
        payload: JSONObject?,
        authenticated: Boolean,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = BuildConfig.MEDIVOX_BACKEND_URL.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")

        if (authenticated) {
            val token = SecureSessionStore(appContext).read()
                ?: throw IOException("Please sign in again to continue.")
            builder.header("Authorization", "Bearer $token")
        }

        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.post(
                (payload ?: JSONObject()).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val detail = json.optString("detail")
                throw IOException(
                    detail.takeIf { it.isNotBlank() }
                        ?: "MediVox server returned HTTP ${response.code}"
                )
            }
            json
        }
    }
}

class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "medivox_secure_session",
        Context.MODE_PRIVATE
    )

    fun save(accessToken: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(accessToken.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "medivox_auth_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CIPHERTEXT = "access_token"
        private const val KEY_IV = "access_token_iv"
    }
}

class AuthRepository(context: Context) {
    private val api = BackendApi(context)
    private val tokenStore = SecureSessionStore(context)

    suspend fun register(email: String, password: String, displayName: String): AppUser {
        val response = api.post(
            "/auth/register",
            JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("display_name", displayName.trim()),
            authenticated = false,
        )
        tokenStore.save(response.getString("access_token"))
        return userFrom(response.getJSONObject("user"))
    }

    suspend fun login(email: String, password: String): AppUser {
        val response = api.post(
            "/auth/login",
            JSONObject().put("email", email.trim()).put("password", password),
            authenticated = false,
        )
        tokenStore.save(response.getString("access_token"))
        return userFrom(response.getJSONObject("user"))
    }

    suspend fun currentUser(): AppUser {
        val response = api.get("/auth/me")
        return userFrom(response.getJSONObject("user"))
    }

    fun logout() = tokenStore.clear()

    private fun userFrom(json: JSONObject) = AppUser(
        id = json.getString("id"),
        email = json.getString("email"),
        displayName = json.optString("display_name", ""),
    )
}

class ConversationRepository(context: Context) {
    private val api = BackendApi(context)

    suspend fun create(title: String): String =
        api.post("/api/conversations", JSONObject().put("title", title))
            .getJSONObject("conversation")
            .getString("id")

    suspend fun addMessage(conversationId: String, role: String, content: String) {
        api.post(
            "/api/conversations/$conversationId/messages",
            JSONObject().put("role", role).put("content", content)
        )
    }

    suspend fun recent(): List<ConversationSummary> {
        val items: JSONArray = api.get("/api/conversations").getJSONArray("conversations")
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    ConversationSummary(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        updatedAt = item.optString("updated_at"),
                    )
                )
            }
        }
    }
}

class RealtimeTokenProvider(context: Context) {
    private val api = BackendApi(context)

    suspend fun createClientSecret(): String =
        api.post("/api/realtime/client-secret")
            .getString("value")
}

fun JSONObject.optJSONArrayOrEmpty(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
