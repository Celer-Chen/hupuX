package com.hupux.data

interface CookieStorage {
    val isLoggedIn: Boolean
    val effectiveCookie: String
    fun extractUid(): String?
}
