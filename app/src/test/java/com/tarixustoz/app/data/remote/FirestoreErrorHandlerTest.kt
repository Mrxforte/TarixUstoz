package com.tarixustoz.app.data.remote

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirestoreErrorHandlerTest {

    @Test
    fun offlineException_isClassifiedAsTransientOffline() {
        val offlineException = FirebaseFirestoreException(
            "Failed to get document because the client is offline.",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )
        assertTrue(offlineException.isFirestoreOfflineOrTransient())
        assertTrue(RuntimeException("wrapper", offlineException).isFirestoreOfflineOrTransient())
    }

    @Test
    fun permissionDenied_isNotClassifiedAsTransientOffline() {
        val permissionDenied = FirebaseFirestoreException(
            "Missing or insufficient permissions.",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        assertFalse(permissionDenied.isFirestoreOfflineOrTransient())
    }
}
