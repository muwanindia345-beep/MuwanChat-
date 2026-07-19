package com.muwan.muwanchat.util

import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.io.IOException

// Raw exceptions (UnknownHostException, SocketTimeoutException, etc.) look ugly
// if shown directly to the user. This converts them into a single clean,
// consistent message across the app.
fun friendlyErrorMessage(e: Throwable): String {
    return when (e) {
        is UnknownHostException,
        is ConnectException -> "Network error, please try again"
        is SocketTimeoutException -> "Request timed out, please try again"
        is IOException -> "Network error, please try again"
        else -> e.message ?: "Something went wrong, please try again"
    }
}
