package com.example.template.api

import com.example.template.cli.SetRoleCommand
import com.example.template.support.NoScheduledEmailWorker
import com.example.template.support.TestEnv
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
import javax.sql.DataSource

/**
 * The out-of-band role grant: valid roles update, unknown emails and invalid
 * roles are rejected.
 *
 * The CLI wrapper and the role change itself are separate
 * ([SetRoleCommand.run] vs [SetRoleCommand.apply]), so the grant is
 * exercised against this suite's database rather than a dev one.
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
class SetRoleSubcommandTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var dataSource: DataSource

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
    fun set_role_subcommand() {
        env.signup()

        // Argument handling, before any database work: unknown role, and the
        // wrong number of arguments.
        assertEquals(
            1,
            SetRoleCommand.run(arrayOf(env.email, "wizard")),
        )
        assertEquals(1, SetRoleCommand.run(arrayOf(env.email)))

        // No such user, through the CLI wrapper's own DSN resolution.
        assertEquals(
            1,
            SetRoleCommand.run(arrayOf("nobody@mail.com", "admin")),
        )

        // The grant itself, against this suite's database.
        dataSource.connection.use { connection ->
            assertEquals(
                SetRoleCommand.Outcome.OK,
                SetRoleCommand.apply(connection, env.email, "staff"),
            )
            assertEquals(
                SetRoleCommand.Outcome.NO_SUCH_USER,
                SetRoleCommand.apply(connection, "nobody@mail.com", "staff"),
            )
            assertEquals(
                SetRoleCommand.Outcome.INVALID_ROLE,
                SetRoleCommand.apply(connection, env.email, "wizard"),
            )
        }
        assertEquals("staff", env.roleOf(env.email))
    }
}
