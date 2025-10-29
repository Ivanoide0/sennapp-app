package com.senapp.data

import android.util.Log
import com.senapp.model.SignSpec

object SignsRepository {

    /** Intenta Firebase primero; si falla o viene vacío, usa MediaFire (url puede ser null). */
    suspend fun load(firebaseFirst: Boolean, mediafireUrl: String?): List<SignSpec> {
        return try {
            val fb = if (firebaseFirst) FirebaseSignsRepository.fetch().signs else emptyList()
            if (fb.isNotEmpty()) fb
            else {
                if (!mediafireUrl.isNullOrBlank()) RemoteSignsRepository.fetch(mediafireUrl).signs
                else emptyList()
            }
        } catch (e: Exception) {
            Log.e("SignsRepository", "Firebase/MediaFire error: ${e.message}")
            if (!mediafireUrl.isNullOrBlank()) {
                try { RemoteSignsRepository.fetch(mediafireUrl).signs } catch (_: Exception) { emptyList() }
            } else emptyList()
        }
    }
}
