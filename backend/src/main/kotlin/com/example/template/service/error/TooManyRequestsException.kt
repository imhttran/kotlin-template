package com.example.template.service.error

/**
 * Rate limit hit. Mapped to a 429.
 */
class TooManyRequestsException(message: String) : RuntimeException(message)
