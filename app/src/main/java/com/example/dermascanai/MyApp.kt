package com.example.dermascanai
 // use your actual package name

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ Enable offline persistence
        FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").setPersistenceEnabled(true)

        // (Optional) Cache specific data always, even if not currently being used
        FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users").keepSynced(true)
        FirebaseDatabase.getInstance("https://dermascanai-2d7a1-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("posts").keepSynced(true)
    }
}
