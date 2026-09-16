package com.example.template.api

import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
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
 * Client rejected, staff allowed (non-admin rows only), admin allowed.
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
class UsersRbacTest {

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
    fun users_rbac() {
        env.signup()
        val token = env.loginAs(env.email, env.password)
        env.fillProfile(token) // lift the profile gate so RBAC is what's under test

        // Client is rejected.
        assertStatus(403, env.doJson("GET", "/api/users", token, null))

        // Staff is allowed.
        env.setRole(env.email, "staff")
        val staff = env.doJson("GET", "/api/users", token, null)
        assertStatus(200, staff)
        assertTrue(
            staff.body.path("users").isArray(),
        ) { "staff: missing users key: ${staff.text}" }

        // Admin is allowed.
        env.setRole(env.email, "admin")
        assertStatus(200, env.doJson("GET", "/api/users", token, null))
    }
}
