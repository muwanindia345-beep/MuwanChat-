package com.muwan.muwanchat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Upload shuru karne se pehle quick check — agar network hi nahi hai to
// hum seedha FAILED maar denge, koi "uploading" progress layer nahi dikhayenge.
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}
