// file: src/main/java/com/senapp/data/FirebaseSignsRepository.kt
package com.senapp.data

import com.google.firebase.database.*
import com.senapp.model.Finger
import com.senapp.model.Rule
import com.senapp.model.SignDb
import com.senapp.model.SignSpec
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseSignsRepository {

    // Forzamos la URL de tu RTDB (usa la que te muestra la consola de Realtime Database)
    private val db by lazy {
        FirebaseDatabase.getInstance("https://senapp-2bfa1-default-rtdb.firebaseio.com/")
        // Si tu instancia es regional, sería algo como:
        // FirebaseDatabase.getInstance("https://senapp-2bfa1-default-rtdb.northamerica-south1.firebasedatabase.app")
    }

    suspend fun fetch(): SignDb = suspendCancellableCoroutine { cont ->
        val ref = db.getReference("/") // raíz

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val version = readInt(snapshot.child("meta"), "version") ?: 1
                    val signsSnap = snapshot.child("signs")
                    val list = ArrayList<SignSpec>()

                    for (signNode in signsSnap.children) {
                        val id = signNode.child("id").getValue(String::class.java) ?: (signNode.key ?: "")
                        val labelEs = signNode.child("label_es").getValue(String::class.java) ?: id
                        val labelEn = signNode.child("label_en").getValue(String::class.java)
                        val type = signNode.child("type").getValue(String::class.java) ?: "static"
                        val handedness = signNode.child("handedness").getValue(String::class.java)
                        val smoothingMs = readInt(signNode, "smoothing_ms") ?: 400
                        val sampleCount = readInt(signNode, "sample_count") ?: 1
                        val notes = signNode.child("notes").getValue(String::class.java)

                        val rules = ArrayList<Rule>()
                        for (r in signNode.child("rules").children) {
                            val metric = r.child("metric").getValue(String::class.java) ?: continue

                            val fingerStr = r.child("finger").getValue(String::class.java)
                            val finger = parseFinger(fingerStr)

                            val finger2Str = r.child("finger2").getValue(String::class.java)
                            val finger2 = parseFinger(finger2Str)

                            val min = readFloat(r, "min")
                            val max = readFloat(r, "max")

                            rules += Rule(
                                metric = metric,
                                finger = finger,
                                finger2 = finger2,
                                min = min,
                                max = max
                            )
                        }

                        list += SignSpec(
                            id = id,
                            labelEs = labelEs,
                            labelEn = labelEn,
                            type = type,
                            handedness = handedness,
                            rules = rules,
                            smoothingMs = smoothingMs,
                            sampleCount = sampleCount,
                            notes = notes
                        )
                    }

                    cont.resume(SignDb(version = version, signs = list))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                cont.resumeWithException(error.toException())
            }
        }

        ref.addListenerForSingleValueEvent(listener)
        cont.invokeOnCancellation { ref.removeEventListener(listener) }
    }

    // ===== Helpers =====

    private fun parseFinger(name: String?): Finger? = when (name?.lowercase()) {
        "thumb" -> Finger.THUMB
        "index" -> Finger.INDEX
        "middle" -> Finger.MIDDLE
        "ring" -> Finger.RING
        "pinky" -> Finger.PINKY
        else -> null
    }

    /** Lee un entero desde parent.child(key) tolerando Long/Double/Int/String. */
    private fun readInt(parent: DataSnapshot, key: String): Int? {
        val v = parent.child(key).value
        return when (v) {
            is Long -> v.toInt()
            is Int -> v
            is Double -> v.toInt()
            is Float -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    /** Lee un float desde parent.child(key) tolerando Double/Long/Int/Float/String. */
    private fun readFloat(parent: DataSnapshot, key: String): Float? {
        val v = parent.child(key).value
        return when (v) {
            is Double -> v.toFloat()
            is Float -> v
            is Long -> v.toFloat()
            is Int -> v.toFloat()
            is String -> v.toFloatOrNull()
            else -> null
        }
    }
}
