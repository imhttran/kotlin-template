package com.example.template.api

import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

/** /api/me with no token, with a bad token, and with a valid one. */
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
class MeRequiresTokenTest {

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
    fun me_requires_token() {
        assertStatus(401, env.doJson("GET", "/api/me", "", null))

        env.signup()
        val token = env.loginAs(env.email, env.password)

        val me = env.doJson("GET", "/api/me", token, null)
        assertStatus(200, me)
        assertEquals(env.email, me.body.path("user").path("email").asText()) { "me.user.email = ${me.text}" }
    }
}
