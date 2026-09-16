package com.example.template.api

import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
import com.example.template.support.assertMessage
import com.example.template.support.assertStatus
import org.junit.jupiter.api.AfterEach
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

/** Patching a role to something that is not client|staff|admin. */
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
class PatchRoleInvalidValueTest {

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
    fun patch_role_invalid_value() {
        env.signup()
        val token = env.loginAs(env.email, env.password)
        env.fillProfile(token)
        env.setRole(env.email, "admin")

        val id = env.ownUserId(token)
        val response = env.doJson(
            "PATCH",
            "/api/users/$id/role",
            token,
            TestEnv.json("role", "wizard"),
        )
        assertStatus(400, response)
        assertMessage("role must be one of: client, staff, admin", response)
    }
}
