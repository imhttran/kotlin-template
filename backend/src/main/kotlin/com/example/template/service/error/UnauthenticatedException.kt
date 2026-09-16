package com.example.template.service.error

/**
 * Bad credentials. Mapped to a 401.
 */
class UnauthenticatedException(message: String) : RuntimeException(message)
