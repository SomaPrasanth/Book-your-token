package com.example.bookyourtoken.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

// Ktor's OkHttp engine has no cookie jar of its own, so HttpCookies is the only session store.
internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) { config() }
