package com.example.dermascanai
 // use your actual package name

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ Enable offline persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // (Optional) Cache specific data always, even if not currently being used
        FirebaseDatabase.getInstance().getReference("users").keepSynced(true)
        FirebaseDatabase.getInstance().getReference("posts").keepSynced(true)
    }
}
