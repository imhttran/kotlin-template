package com.example.template.api

import com.example.template.api.dto.ChangePasswordBody
import com.example.template.api.dto.EmailBody
import com.example.template.api.dto.LoginBody
import com.example.template.api.dto.ResendCodeBody
import com.example.template.api.dto.ResetPasswordBody
import com.example.template.api.dto.SignupBody
import com.example.template.api.dto.VerifyLoginBody
import com.example.template.service.AuthService
import com.example.template.service.AuthUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Public auth routes plus the signed-in self-service ones. Bodies are read as
 * raw bytes so an unparsable body behaves like an empty one (see [Api]).
 */
@RestController
@RequestMapping("/api")
class AuthController(private val auth: AuthService) {

    @PostMapping("/signup")
    fun signup(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, SignupBody::class.java)
        auth.signup(request.email, request.password)
        return Api.respond(
            HttpStatus.CREATED,
            Api.body(
                "success", true,
                "message", "User created successfully!",
                "user", Api.body("email", request.email),
            ),
        )
    }

    @GetMapping("/verify")
    fun verify(@RequestParam(name = "token", required = false) token: String?): ResponseEntity<Any> {
        auth.verify(token ?: "")
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "Email verified successfully!",
            ),
        )
    }

    @PostMapping("/resend-verification")
    fun resendVerification(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, EmailBody::class.java)
        auth.resendVerification(request.email)
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "If that email is registered and unverified, a verification link has been sent.",
            ),
        )
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, EmailBody::class.java)
        auth.forgotPassword(request.email)
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "If that email is registered, a reset link has been sent.",
            ),
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, ResetPasswordBody::class.java)
        val reset = auth.resetPassword(request.token, request.password)
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "Password reset successfully!",
                "token", reset.token,
                "user", Api.body("email", reset.email),
            ),
        )
    }

    @PostMapping("/login")
    fun login(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, LoginBody::class.java)
        return loginResponse(auth.login(request.email, request.password, request.deviceId))
    }

    @PostMapping("/login/verify")
    fun verifyLogin(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, VerifyLoginBody::class.java)
        return loginResponse(auth.verifyLogin(request.token, request.code, request.deviceId))
    }

    @PostMapping("/login/resend")
    fun resendLoginCode(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = Api.decode(body, ResendCodeBody::class.java)
        auth.resendLoginCode(request.token)
        return Api.respond(HttpStatus.OK, Api.msg("Code resent"))
    }

    @GetMapping("/me")
    fun me(user: AuthUser): ResponseEntity<Any> = Api.respond(
        HttpStatus.OK,
        Api.body(
            "message", "Welcome to the secret area!",
            "user", Api.body(
                "id", user.id,
                "email", user.email,
                "role", user.role,
                "emailVerified", user.emailVerified,
                "mustChangePassword", user.mustChangePassword,
                "hasProfile", user.hasProfile,
            ),
        ),
    )

    @PostMapping("/change-password")
    fun changePassword(
        user: AuthUser,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<Any> {
        val request = Api.decode(body, ChangePasswordBody::class.java)
        auth.changePassword(user.id, user.passwordHash, request.currentPassword, request.newPassword)
        return Api.respond(
            HttpStatus.OK,
            Api.body(
                "success", true,
                "message", "Password changed successfully!",
            ),
        )
    }

    private companion object {
        /** A trusted device logs straight in; a new one gets a pending 2FA token instead. */
        private fun loginResponse(result: AuthService.LoginResult): ResponseEntity<Any> = when (result) {
            is AuthService.LoginResult.Authenticated -> Api.respond(
                HttpStatus.OK,
                Api.body(
                    "success", true,
                    "message", "Login successful!",
                    "token", result.token,
                    "user", Api.body("email", result.email),
                ),
            )

            is AuthService.LoginResult.TwoFactorRequired -> Api.respond(
                HttpStatus.OK,
                Api.body(
                    "success", true,
                    "twoFactorRequired", true,
                    "token", result.pendingToken,
                    "message", "Enter the code sent to your device",
                ),
            )
        }
    }
}
