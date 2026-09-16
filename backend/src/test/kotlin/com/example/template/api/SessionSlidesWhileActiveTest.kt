package com.example.template.api

import com.example.template.service.JwtService
import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
 * A token deep
 * into its life is renewed on successful use (X-Renewed-Token, produced by
 * [SessionRenewalFilter]), the renewed token is a normal bearer, a
 * full-life token is not renewed, and a hard-expired one is rejected.
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
class SessionSlidesWhileActiveTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var jwt: JwtService

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
    fun session_slides_while_active() {
        env.signup()

        // 60 seconds left of the 10-minute window → renewed on use.
        val aging = jwt.issueWithTtl(env.email, 60L)
        val sliding = env.doJson("GET", "/api/me", aging, null)
        assertStatus(200, sliding)
        val renewed = sliding.renewedToken
        assertNotNull(renewed, "aging token should be renewed")
        assertNotEquals(aging, renewed, "renewal must be a new token")

        // The renewed token is a normal bearer.
        assertStatus(200, env.doJson("GET", "/api/me", renewed, null))

        // A fresh (full-life) token is not renewed.
        val fresh = jwt.issue(env.email)
        val full = env.doJson("GET", "/api/me", fresh, null)
        assertStatus(200, full)
        assertNull(full.renewedToken, "full-life token should not renew")

        // Hard expiry: past the window, the token is rejected outright.
        val expired = jwt.issueWithTtl(env.email, -60L)
        assertStatus(403, env.doJson("GET", "/api/me", expired, null))
    }
}
