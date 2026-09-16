package com.example.template.api

import com.example.template.api.dto.CreateUserBody
import com.example.template.api.dto.PatchRoleBody
import com.example.template.service.AuthUser
import com.example.template.service.Roles
import com.example.template.service.UserAdminService
import com.example.template.service.error.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Staff/admin user management. The role gate runs before the path id is parsed,
 * because that is the order the original checked them in.
 */
@RestController
@RequestMapping("/api/users")
class UsersController(private val admin: UserAdminService) {

    @GetMapping
    fun listUsers(user: AuthUser): ResponseEntity<Any> {
        Api.requireRole(user, "staff")
        return Api.respond(
            HttpStatus.OK,
            Api.body("users", admin.listUsers(Roles.hasRole(user.role, "admin"))),
        )
    }

    @PostMapping
    fun createUser(
        user: AuthUser,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<Any> {
        Api.requireRole(user, "admin")
        val request = Api.decode(body, CreateUserBody::class.java)
        val created = admin.createUser(request.email, request.password)
        return Api.respond(
            HttpStatus.CREATED,
            Api.body(
                "success", true,
                "message", "User created successfully!",
                "user", Api.body(
                    "id", created.id,
                    "email", created.email,
                    "role", created.role,
                    "emailVerified", created.emailVerified,
                ),
            ),
        )
    }

    @DeleteMapping("/{id}")
    fun deleteUser(user: AuthUser, @PathVariable("id") id: String): ResponseEntity<Any> {
        Api.requireRole(user, "admin")
        val userId = Api.parseId(id)
        try {
            admin.deleteUser(user.id, userId)
        } catch (rejected: ValidationException) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.message))
        }
        return Api.respond(HttpStatus.OK, Api.body("success", true, "message", "User deleted"))
    }

    @PatchMapping("/{id}/verification")
    fun patchVerification(
        user: AuthUser,
        @PathVariable("id") id: String,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<Any> {
        Api.requireRole(user, "admin")
        val userId = Api.parseId(id)
        // Decoded loosely: a present-but-non-boolean value (string, number)
        // reads as not-a-boolean.
        val emailVerified = Api.decodeNode(body).path("emailVerified")
        if (!emailVerified.isBoolean) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg("emailVerified must be a boolean"))
        }
        val updated = admin.setVerification(userId, emailVerified.booleanValue())
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", if (updated.emailVerified) "User marked as verified" else "User marked as unverified",
                "user", Api.body(
                    "id", updated.id,
                    "email", updated.email,
                    "emailVerified", updated.emailVerified,
                ),
            ),
        )
    }

    @PatchMapping("/{id}/role")
    fun patchRole(
        user: AuthUser,
        @PathVariable("id") id: String,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<Any> {
        Api.requireRole(user, "admin")
        val userId = Api.parseId(id)
        val request = Api.decode(body, PatchRoleBody::class.java)
        val updated = try {
            admin.setRole(user.id, userId, request.role)
        } catch (rejected: ValidationException) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.message))
        }
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "User role updated",
                "user", Api.body(
                    "id", updated.id,
                    "email", updated.email,
                    "role", updated.role,
                ),
            ),
        )
    }

    @PostMapping("/{id}/resend-verification")
    fun resendVerification(user: AuthUser, @PathVariable("id") id: String): ResponseEntity<Any> {
        Api.requireRole(user, "staff")
        val userId = Api.parseId(id)
        try {
            admin.resendVerification(userId)
        } catch (rejected: ValidationException) {
            return Api.respond(HttpStatus.BAD_REQUEST, Api.msg(rejected.message))
        }
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "Verification email sent",
            ),
        )
    }

    @PostMapping("/{id}/reset-password")
    fun resetPassword(user: AuthUser, @PathVariable("id") id: String): ResponseEntity<Any> {
        Api.requireRole(user, "admin")
        admin.resetPassword(Api.parseId(id))
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "Password reset email sent",
            ),
        )
    }
}
