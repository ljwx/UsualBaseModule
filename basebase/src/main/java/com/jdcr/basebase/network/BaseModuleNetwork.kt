package com.jdcr.basebase.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission


@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
@RequiresApi(Build.VERSION_CODES.M)
inline fun isNetworkConnected(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val network = connectivityManager?.activeNetwork
    if (network != null) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }
    return false
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
@RequiresApi(Build.VERSION_CODES.M)
inline fun isWifiConnected(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager != null) {
        val network = connectivityManager.activeNetwork
        if (network != null) {
            return connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
    return false
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
@RequiresApi(Build.VERSION_CODES.M)
inline fun isMobileDataConnected(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager != null) {
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }
    return false
}