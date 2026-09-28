package com.example.bookyourtoken.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        engine {
            configureSession {
                // NSURLSession would otherwise share cookies app-wide and across launches. Ktor's
                // HttpCookies must be the only jar so each HostelClient is a genuinely fresh session.
                HTTPCookieStorage = null
                HTTPShouldSetCookies = false
            }
        }
        config()
    }
