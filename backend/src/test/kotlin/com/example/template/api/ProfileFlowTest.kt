package com.example.template.api

import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertMessage
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
 * The gate lift, validation and the unique-violation path, end to end.
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
class ProfileFlowTest {

    /** One rejected form and the message the route must answer it with. */
    private data class InvalidCase(val payload: Map<String, Any?>, val want: String)

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
    fun profile_flow() {
        env.signup()
        val token = env.loginAs(env.email, env.password)

        // Before saving, the profile is a 200 with null (the absence is the gate).
        val empty = env.doJson("GET", "/api/profile", token, null)
        assertStatus(200, empty)
        assertTrue(
            empty.body.path("profile").isNull(),
        ) { "body ${empty.text}" }

        env.fillProfile(token)

        // Second save hits the unique constraint → "Profile already exists".
        val duplicate = env.doJson(
            "POST",
            "/api/profile",
            token,
            TestEnv.profileBody(),
        )
        assertStatus(400, duplicate)
        assertMessage("Profile already exists", duplicate)

        // The saved row comes back with camelCase fields and the US default.
        val saved = env.doJson("GET", "/api/profile", token, null)
        assertStatus(200, saved)
        assertEquals(
            "Test",
            saved.body.path("profile").path("firstName").asText(),
        ) { "body ${saved.text}" }
        assertEquals(
            "email",
            saved
                .body
                .path("profile")
                .path("communicationPreference")
                .asText(),
        ) { "body ${saved.text}" }
        assertEquals(
            "US",
            saved.body.path("profile").path("country").asText(),
        ) { "body ${saved.text}" }
        assertTrue(
            saved.body.path("profile").path("address2").isNull(),
        ) { "body ${saved.text}" }

        // Validation runs before the insert, so these 400s don't mention profiles.
        val cases = listOf(
            InvalidCase(
                TestEnv.json(
                    "firstName", "Test",
                    "lastName", "User",
                    "address", "1 Test St",
                    "state", "CA",
                    "zip", "94043",
                    "phone", "not-a-phone",
                    "communicationPreference", "email",
                ),
                "Phone number is invalid",
            ),
            InvalidCase(
                TestEnv.json(
                    "firstName", "Test",
                    "lastName", "User",
                    "address", "1 Test St",
                    "state", "XX",
                    "zip", "94043",
                    "phone", "555-123-4567",
                    "communicationPreference", "email",
                ),
                "State is invalid",
            ),
            InvalidCase(
                TestEnv.json(
                    "firstName", "Test",
                    "lastName", "User",
                    "address", "1 Test St",
                    "state", "CA",
                    "phone", "555-123-4567",
                    "communicationPreference", "email",
                ),
                "Missing required field(s): zip",
            ),
        )
        for (rejected in cases) {
            val response = env.doJson(
                "POST",
                "/api/profile",
                token,
                rejected.payload,
            )
            assertStatus(400, response)
            assertMessage(rejected.want, response)
        }
    }
}
