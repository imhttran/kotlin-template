package com.example.template.service.error

/**
 * Something went wrong on our side. The [context] is logged (the reason is
 * never sent to the client) and [withSuccess] picks which of the two response
 * body shapes this endpoint uses.
 */
class ServerErrorException(
    val context: String,
    cause: Throwable,
    val withSuccess: Boolean,
) : RuntimeException("$context: ${cause.message}", cause)
