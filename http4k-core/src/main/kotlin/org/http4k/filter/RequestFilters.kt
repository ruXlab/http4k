package org.http4k.filter

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Uri

object RequestFilters {

    /**
     * Intercept the request before it is sent to the next service.
     */
    object Tap {
        operator fun invoke(fn: (Request) -> Unit) = Filter { next ->
            HttpHandler {
                fn(it)
                next(it)
            }
        }
    }

    /**
     * Basic GZipping of Request. Does not currently support GZipping streams
     */
    object GZip {
        operator fun invoke() = Filter { next ->
            HttpHandler { request ->
                next(request.body(request.body.gzipped()).replaceHeader("content-encoding", "gzip"))
            }
        }
    }

    /**
     * Basic UnGZipping of Request. Does not currently support GZipping streams
     */
    object GunZip {
        operator fun invoke() = Filter { next ->
            HttpHandler { request ->
                request.header("content-encoding")
                    ?.let { if (it.contains("gzip")) it else null }
                    ?.let { next(request.body(request.body.gunzipped())) } ?: next(request)
            }
        }
    }

    enum class ProxyProtocolMode(private val fn: (Uri) -> Uri) {
        Http({ it.scheme("http") }),
        Https({ it.scheme("https") }),
        Port({
            when (it.port) {
                443 -> Https(it)
                else -> Http(it)
            }
        });

        operator fun invoke(uri: Uri) = fn(uri)
    }

    /**
     * Sets the host on an outbound request from the Host header of the incoming request. This is useful for implementing proxies.
     * Note the use of the ProxyProtocolMode to set the outbound scheme
     */
    object ProxyHost {
        operator fun invoke(mode: ProxyProtocolMode = ProxyProtocolMode.Http) = Filter { next ->
            HttpHandler {
                it.header("Host")?.let { host -> next(it.uri(mode(it.uri).authority(host))) }
                    ?: Response(BAD_REQUEST.description("Cannot proxy without host header"))
            }
        }
    }
}

