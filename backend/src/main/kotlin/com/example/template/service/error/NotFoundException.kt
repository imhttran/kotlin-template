package com.example.template.service.error

/**
 * No such record. Mapped to a 404.
 */
class NotFoundException(message: String) : RuntimeException(message)
