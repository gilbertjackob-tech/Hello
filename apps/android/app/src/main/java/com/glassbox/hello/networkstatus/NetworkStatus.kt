package com.glassbox.hello.networkstatus

sealed class NetworkStatus {
    data object Checking : NetworkStatus()
    data object Connected : NetworkStatus()
    data object HelloApiReachable : NetworkStatus()
    data object Offline : NetworkStatus()
    data object Error : NetworkStatus()
}
