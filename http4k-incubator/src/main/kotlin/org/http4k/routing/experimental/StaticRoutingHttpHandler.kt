package org.http4k.routing.experimental

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.NoOp
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Uri.Companion.of
import org.http4k.core.then
import org.http4k.routing.Router
import org.http4k.routing.RoutingHttpHandler

fun static(resourceLoader: Router): RoutingHttpHandler = StaticRoutingHttpHandler("", resourceLoader)

internal data class StaticRoutingHttpHandler(
    private val pathSegments: String,
    private val resourceLoader: Router,
    private val filter: Filter = Filter.NoOp
) : RoutingHttpHandler {

    override fun withFilter(new: Filter): RoutingHttpHandler = copy(filter = new.then(filter))

    override fun withBasePath(new: String): RoutingHttpHandler = copy(pathSegments = new + pathSegments)

    private val handlerNoFilter = ResourceLoadingHandler(pathSegments, resourceLoader)
    private val handlerWithFilter = filter.then(handlerNoFilter)

    override suspend fun match(request: Request): HttpHandler? = handlerNoFilter(request).let {
        if (it.status != NOT_FOUND) filter.then { _: Request -> it } else null
    }

    override suspend fun invoke(request: Request): Response = handlerWithFilter(request)
}


internal class ResourceLoadingHandler(
    private val pathSegments: String,
    private val resourceLoader: Router
) : HttpHandler {

    override suspend fun invoke(request: Request): Response =
        if (request.method == GET && request.uri.path.startsWith(pathSegments))
            resourceLoader.match(request.uri(of(convertPath(request.uri.path))))?.invoke(request) ?: Response(NOT_FOUND)
        else
            Response(NOT_FOUND)

    private fun convertPath(path: String) =
        if (pathSegments == "/" || pathSegments == "") path else path.replace(pathSegments, "")
}