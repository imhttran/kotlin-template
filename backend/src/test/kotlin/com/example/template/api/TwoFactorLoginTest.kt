package com.example.template.api

import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertJsonNotTrue
import com.example.template.support.assertJsonTrue
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import java.util.function.Supplier

/**
 * New-device login demands a code, wrong codes are rejected, the emailed code yields a real JWT, the
 * device is trusted from then on, and resend rotates the code.
 */
@SpringBootTest(
    properties = [
        TestEnv.APP_ENV_TEST,
        TestEnv.EMAIL_VERIFICATION_NOT_REQUIRED,
        TestEnv.FRONTEND_URL,
        TestEnv.JWT_SECRET,
        TestEnv.ALLOW_BEAN_OVERRIDE,
    ],
)
@AutoConfigureMockMvc
@Import(NoScheduledEmailWorker::class) // these tests drive the queue themselves
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // close the context (and its 10-connection pool) after each class
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class TwoFactorLoginTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcClient

    private lateinit var env: TestEnv

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("app.database-url", Supplier { TestEnv.databaseUrl() })
        }
    }

    @BeforeEach
    fun setUp() {
        env = TestEnv(mvc, jdbc)
    }

    @AfterEach
    fun cleanup() {
        env.cleanup()
    }

    @Test
    fun two_factor_login() {
        assertStatus(201, env.signup())

        // First login from an unknown device → 2FA required, no real JWT.
        val challenged = env.doJson(
            "POST",
            "/api/login",
            "",
            TestEnv.json(
                "email", env.email,
                "password", env.password,
                "deviceId", "dev-1",
            ),
        )
        assertStatus(200, challenged)
        assertJsonTrue("twoFactorRequired", challenged)
        val pending = challenged.body.path("token").asText("")
        assertFalse(
            pending.isEmpty(),
        ) { "2FA login returned no pending token: ${challenged.text}" }

        // Wrong code → 400, and the code stays pending.
        val wrong = env.doJson(
            "POST",
            "/api/login/verify",
            "",
            TestEnv.json("token", pending, "code", "0000", "deviceId", "dev-1"),
        )
        assertStatus(400, wrong)

        // Resend rotates the code; the newest queued email wins.
        val resent = env.doJson(
            "POST",
            "/api/login/resend",
            "",
            TestEnv.json("token", pending),
        )
        assertStatus(200, resent)

        // Correct (resent) code → real JWT that works on /api/me.
        val verified = env.doJson(
            "POST",
            "/api/login/verify",
            "",
            TestEnv.json(
                "token", pending,
                "code", env.fetchLoginCode(env.email),
                "deviceId", "dev-1",
            ),
        )
        assertStatus(200, verified)
        val token = verified.body.path("token").asText("")
        assertTrue(
            !token.isEmpty() && token != pending,
        ) { "verify returned no real token: ${verified.text}" }
        assertStatus(200, env.doJson("GET", "/api/me", token, null))

        // Same device again → 2FA skipped.
        val trusted = env.doJson(
            "POST",
            "/api/login",
            "",
            TestEnv.json(
                "email", env.email,
                "password", env.password,
                "deviceId", "dev-1",
            ),
        )
        assertStatus(200, trusted)
        assertJsonNotTrue("twoFactorRequired", trusted)
        val token2 = trusted.body.path("token").asText("")
        assertTrue(
            !token2.isEmpty() && token2 != pending,
        ) { trusted.text }
    }
}
