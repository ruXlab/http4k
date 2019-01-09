package org.http4k.serverless.lambda

import kotlinx.coroutines.runBlocking
import org.http4k.core.Body
import org.http4k.core.MemoryBody
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.http4k.core.toUrlFormEncoded
import org.http4k.serverless.BootstrapAppLoader

/**
 * This is the main entry point for the lambda. It uses the local environment
 * to instantiate the Http4k handler which can be used for further invocations.
 */
class LambdaFunction(env: Map<String, String> = System.getenv()) {
    private val app = BootstrapAppLoader(env)

    fun handle(request: ApiGatewayProxyRequest) = runBlocking { app(request.asHttp4k()).asApiGateway() }
}

internal fun Response.asApiGateway() = ApiGatewayProxyResponse(status.code, headers.toMap(), bodyString())

internal fun ApiGatewayProxyRequest.asHttp4k() = (headers ?: emptyMap()).toList().fold(
    Request(Method.valueOf(httpMethod), uri())
        .body(body?.let(::MemoryBody) ?: Body.EMPTY)) { memo, (first, second) ->
    memo.header(first, second)
}

internal fun ApiGatewayProxyRequest.uri() = Uri.of(path ?: "").query((queryStringParameters
    ?: emptyMap()).toList().toUrlFormEncoded())
