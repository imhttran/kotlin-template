package com.example.template.service.error

/**
 * The change collided with something that already exists (in practice, a
 * unique email or an existing profile). Mapped to a 400.
 */
class ConflictException(message: String) : RuntimeException(message)
