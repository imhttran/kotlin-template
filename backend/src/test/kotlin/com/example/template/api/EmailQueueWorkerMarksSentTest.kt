package com.example.template.api

import com.example.template.service.EmailQueueService
import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
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
 * The queue worker drains pending rows — signup queues welcome + verification,
 * login queues the 2FA code; with SMTP_HOST unset the log transport succeeds, so
 * everything should flip to 'sent'.
 *
 * This drives [EmailQueueService.process] directly rather than leaving it to the
 * scheduler — see [NoScheduledEmailWorker].
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
class EmailQueueWorkerMarksSentTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var queue: EmailQueueService

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
    fun email_queue_worker_marks_sent() {
        env.signup()
        env.loginAs(env.email, env.password)

        // Drain in rounds — other tests are enqueueing too, so one LIMIT 10
        // batch may not cover this user's rows yet.
        var processed = 0
        for (round in 0 until 10) {
            val taken = queue.process(10)
            processed += taken
            if (taken == 0) {
                break
            }
        }
        val drained = processed
        assertTrue(drained >= 3) {
            "expected at least welcome+verification+login-code, got " +
                drained
        }

        val unsent = jdbc
            .sql(
                "SELECT count(*) FROM email_queue WHERE \"to\" = :email AND status <> 'sent'",
            )
            .param("email", env.email)
            .query(Long::class.javaObjectType)
            .single()
        assertEquals(0L, unsent, "worker left unsent rows behind")
    }
}
