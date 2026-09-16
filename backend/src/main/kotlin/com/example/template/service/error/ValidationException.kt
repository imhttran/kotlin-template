package com.example.template.service.error

/**
 * Input the caller can fix. Mapped to a 400.
 */
class ValidationException(message: String) : RuntimeException(message)
