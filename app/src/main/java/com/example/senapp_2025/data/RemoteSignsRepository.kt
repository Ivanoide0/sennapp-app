package com.senapp.data

import com.senapp.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RemoteSignsRepository {

    suspend fun fetch(url: String): SignDb = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        conn.requestMethod = "GET"; conn.instanceFollowRedirects = true; conn.doInput = true

        conn.inputStream.use { input ->
            val text = input.bufferedReader(Charsets.UTF_8).readText()
            parseJson(text)
        }
    }

    private fun parseJson(text: String): SignDb {
        val root = JSONObject(text)
        val version = root.optInt("version", 1)
        val arr = root.getJSONArray("signs")
        val out = ArrayList<SignSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rulesJ = o.getJSONArray("rules")
            val rules = ArrayList<Rule>(rulesJ.length())
            for (j in 0 until rulesJ.length()) {
                val r = rulesJ.getJSONObject(j)
                val finger = when (r.optString("finger").lowercase()) {
                    "thumb" -> Finger.THUMB
                    "index" -> Finger.INDEX
                    "middle" -> Finger.MIDDLE
                    "ring" -> Finger.RING
                    "pinky" -> Finger.PINKY
                    else -> null
                }
                rules += Rule(
                    metric = r.getString("metric"),
                    finger = finger,
                    min = if (r.has("min")) r.getDouble("min").toFloat() else null,
                    max = if (r.has("max")) r.getDouble("max").toFloat() else null
                )
            }
            out += SignSpec(
                id = o.getString("id"),
                labelEs = o.getString("label_es"),
                labelEn = o.optString("label_en", null),
                type = o.getString("type"),
                handedness = o.optString("handedness", null),
                rules = rules,
                smoothingMs = o.optInt("smoothing_ms", 400),
                sampleCount = o.optInt("sample_count", 1),
                notes = o.optString("notes", null)
            )
        }
        return SignDb(version = version, signs = out)
    }
}
