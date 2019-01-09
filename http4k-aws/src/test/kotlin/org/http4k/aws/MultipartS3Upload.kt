package org.http4k.aws

import kotlinx.coroutines.runBlocking
import org.http4k.core.Body
import org.http4k.core.BodyMode
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.query

object MultipartS3Upload {
    operator fun invoke(size: Int, requestBodyMode: BodyMode) = Filter { next ->
        HttpHandler {
            try {
                val uploadId = UploadId.from(next(it.initialiseMultipart()).orFail())

                val partEtags = it.parts(size)
                    .withIndex()
                    .mapNotNull { (index, part) -> runBlocking { next(it.uploadPart(index, uploadId, requestBodyMode(part))).orFail(uploadId).header("ETag") } }

                next(it.completeMultipart(uploadId, partEtags))
            } catch (e: UploadError) {
                e.uploadId?.run { next(it.terminateMultipart(this)) }
                e.response
            }
        }
    }

    private fun Request.initialiseMultipart() = Request(POST, uri.query("uploads", ""))

    private fun Request.uploadPart(index: Int, uploadId: UploadId, body: Body): Request = Request(Method.PUT, uri
        .query("partNumber", (index + 1).toString())
        .query("uploadId", uploadId.value))
        .body(body)

    private fun Request.completeMultipart(uploadId: UploadId, partEtags: Sequence<String>) = Request(POST, uri.query("uploadId", uploadId.value))
        .body(partEtags.toCompleteMultipartUploadXml())

    private fun Request.terminateMultipart(it: UploadId) = Request(DELETE, uri.query("uploadId", it.value))

    private fun Request.parts(size: Int) = bodyString().run {
        // todo this need to actually work!
        listOf(
            substring((0 until size)),
            substring(size)
        ).asSequence().map { it.byteInputStream() }
    }

    private fun Response.orFail(uploadId: UploadId? = null): Response = apply { if (this.status != Status.OK) throw UploadError(this, uploadId) }

    private data class UploadError(val response: Response, val uploadId: UploadId?) : Exception()
}

internal data class UploadId(val value: String) {
    companion object {
        fun from(response: Response) =
            Regex(""".*UploadId>(.+)</UploadId.*""").find(response.bodyString())?.groupValues?.get(1)!!.let(::UploadId)
    }
}

internal fun Sequence<String>.toCompleteMultipartUploadXml(): String =
    """<CompleteMultipartUpload>${mapIndexed { index, etag ->
        """<Part><PartNumber>${index + 1}</PartNumber><ETag>$etag</ETag></Part>"""
    }.joinToString("")}</CompleteMultipartUpload>"""
