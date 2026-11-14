package com.family.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GistDiscoveryManager {
    private const val TAG = "GistDiscovery"
    private const val GIST_API_URL = "https://api.github.com/gists/4e0a00b1d77298132cdd79e44d7766b3"
    private const val TARGET_FILE = "family_remote_url.txt"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchDynamicUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GIST_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "FamilyAndroid-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gist API request failed with code: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val files = json.optJSONObject("files") ?: return@withContext null
            val fileObj = files.optJSONObject(TARGET_FILE) ?: return@withContext null
            val content = fileObj.optString("content")?.trim() ?: ""

            if (content.isNotEmpty()) {
                val wsUrl = RemoteCommandParser.convertToWebSocketUrl(content)
                Log.i(TAG, "Lấy URL từ Gist thành công: $content -> $wsUrl")
                return@withContext wsUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy URL từ Gist: ${e.message}")
        }
        return@withContext null
    }
}
