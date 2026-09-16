package com.example.template.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** The one-time registration form. One row per user, or none at all. */
@Repository
class ProfileRepository(private val jdbc: JdbcClient) {

    fun findByUserId(userId: Int): Profile? = jdbc
        .sql("SELECT $COLUMNS FROM user_profiles WHERE user_id = :userId")
        .param("userId", userId)
        .query(Profile::class.java)
        .optional()
        .orElse(null)

    /** `profile.id` is ignored — the row's own id comes back. */
    fun insert(profile: Profile): Profile = jdbc
        .sql(
            """
            INSERT INTO user_profiles
                (user_id, first_name, last_name, address, address2, state, zip, country,
                 phone, communication_preference, linkedin, github, alt_email)
            VALUES
                (:userId, :firstName, :lastName, :address, :address2, :state, :zip, :country,
                 :phone, :communicationPreference, :linkedin, :github, :altEmail)
            RETURNING %s
            """.format(COLUMNS),
        )
        .param("userId", profile.userId)
        .param("firstName", profile.firstName)
        .param("lastName", profile.lastName)
        .param("address", profile.address)
        .param("address2", profile.address2)
        .param("state", profile.state)
        .param("zip", profile.zip)
        .param("country", profile.country)
        .param("phone", profile.phone)
        .param("communicationPreference", profile.communicationPreference)
        .param("linkedin", profile.linkedin)
        .param("github", profile.github)
        .param("altEmail", profile.altEmail)
        .query(Profile::class.java)
        .single()

    /** Dev seed: keep whatever profile is already there. */
    fun insertIfAbsent(profile: Profile) {
        jdbc.sql(
            """
            INSERT INTO user_profiles
                (user_id, first_name, last_name, address, address2, state, zip, country,
                 phone, communication_preference, linkedin, github, alt_email)
            VALUES
                (:userId, :firstName, :lastName, :address, :address2, :state, :zip, :country,
                 :phone, :communicationPreference, :linkedin, :github, :altEmail)
            ON CONFLICT (user_id) DO NOTHING
            """,
        )
            .param("userId", profile.userId)
            .param("firstName", profile.firstName)
            .param("lastName", profile.lastName)
            .param("address", profile.address)
            .param("address2", profile.address2)
            .param("state", profile.state)
            .param("zip", profile.zip)
            .param("country", profile.country)
            .param("phone", profile.phone)
            .param("communicationPreference", profile.communicationPreference)
            .param("linkedin", profile.linkedin)
            .param("github", profile.github)
            .param("altEmail", profile.altEmail)
            .update()
    }

    private companion object {
        private const val COLUMNS =
            "id, user_id AS \"userId\", first_name AS \"firstName\", last_name AS \"lastName\", " +
                "address, address2, state, zip, country, phone, " +
                "communication_preference AS \"communicationPreference\", linkedin, github, " +
                "alt_email AS \"altEmail\""
    }
}
