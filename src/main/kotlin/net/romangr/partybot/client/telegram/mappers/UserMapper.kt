package net.romangr.partybot.client.telegram.mappers

import com.elbekd.bot.types.User
import net.romangr.partybot.party.UnknownUser

object UserMapper {
    fun fromTelegramUser(user: User): UnknownUser {
        val firstName = user.first_name
        val lastName = user.lastName
        val username = user.username
        val senderName = listOfNotNull(firstName, lastName).joinToString(" ").let { it.ifBlank { username ?: "User" } }
        return UnknownUser(senderName, user.id, username)
    }
}
