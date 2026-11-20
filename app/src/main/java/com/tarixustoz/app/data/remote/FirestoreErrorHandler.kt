package com.tarixustoz.app.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun Throwable.isFirestoreOfflineOrTransient(): Boolean {
    val chain = generateSequence(this) { it.cause }
    return chain.any { err ->
        (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.UNAVAILABLE) ||
            (err.message?.contains("client is offline", ignoreCase = true) == true) ||
            (err.message?.contains("unavailable", ignoreCase = true) == true)
    }
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    auth: FirebaseAuth
) {
    if (error.isFirestoreOfflineOrTransient()) {
        Log.w(
            "FirestoreSync",
            "Firestore offline/unavailable (${operationType.value} $path): ${error.message}"
        )
        return
    }

    val user = auth.currentUser
    val providersArray = org.json.JSONArray()
    user?.providerData?.forEach { profile ->
        val pObj = JSONObject()
        pObj.put("providerId", profile.providerId)
        pObj.put("displayName", profile.displayName ?: JSONObject.NULL)
        pObj.put("email", profile.email ?: JSONObject.NULL)
        pObj.put("photoUrl", profile.photoUrl?.toString() ?: JSONObject.NULL)
        providersArray.put(pObj)
    }

    val authInfo = JSONObject().apply {
        put("userId", user?.uid ?: JSONObject.NULL)
        put("email", user?.email ?: JSONObject.NULL)
        put("emailVerified", user?.isEmailVerified ?: false)
        put("isAnonymous", user?.isAnonymous ?: false)
        put("tenantId", user?.tenantId ?: JSONObject.NULL)
        put("providerInfo", providersArray)
    }

    val payload = JSONObject().apply {
        put("error", error.message ?: "Unknown Firestore error")
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfo)
    }

    Log.e("FirestoreError", "Firestore Error: $payload", error)
}
