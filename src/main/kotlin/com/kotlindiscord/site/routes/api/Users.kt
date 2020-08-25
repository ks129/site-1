package com.kotlindiscord.site.routes.api

import com.kotlindiscord.api.client.models.UserModel
import com.kotlindiscord.database.Role
import com.kotlindiscord.database.User
import com.kotlindiscord.database.getOrNull
import com.kotlindiscord.site.components.apiRoute
import com.kotlindiscord.site.models.fromDB
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import mu.KotlinLogging
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private val logger = KotlinLogging.logger {}


val apiUsersGet = apiRoute {
    newSuspendedTransaction {
        User.all().map { fromDB(it) }
    }
}

@KtorExperimentalAPI
val apiUsersGetSingle = apiRoute {
    newSuspendedTransaction {
        val id = call.parameters.getOrFail("id").toLong()
        val user = User.getOrNull(id) ?: return@newSuspendedTransaction call.respond(HttpStatusCode.NotFound)

        fromDB(user)
    }
}

val apiUsersPost = apiRoute {
    val model = call.receive<UserModel>()

    newSuspendedTransaction {
        try {
            val user = User[model.id]

            user.userName = model.username
            user.discriminator = model.discriminator
            user.avatarUrl = model.avatarUrl
            user.present = model.present

            val givenRoles = user.roles.map { it.id.value }.toSet()
            val roles = user.roles.filter { model.roles.contains(it.id.value) }.toMutableList()

            for (roleId in model.roles - givenRoles) {
                val role = Role.getOrNull(roleId) ?: return@newSuspendedTransaction call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Unknown role ID: $roleId")
                )

                roles.add(role)
            }

            user.roles = SizedCollection(roles)

            null  // Return nothing if we're successful
        } catch (e: EntityNotFoundException) {
            logger.debug(e) { "Entity not found: ${model.id}" }

            User.new(model.id) {
                userName = model.username
                discriminator = model.discriminator
                avatarUrl = model.avatarUrl
                present = model.present
            }

            commit()  // Exposed has a stupid bug that requires us to add roles after user creation is committed

            val user = User[model.id]
            val roles = mutableListOf<Role>()

            for (roleId in model.roles) {
                val role = Role.getOrNull(roleId) ?: return@newSuspendedTransaction call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Unknown role ID: $roleId")
                )

                roles.add(role)
            }

            user.roles = SizedCollection(roles)

            null  // Return nothing if we're successful
        }
    }
}
