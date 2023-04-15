package net.romangr.partybot.client.telegram.views

import com.elbekd.bot.types.InlineKeyboardButton
import com.elbekd.bot.types.InlineKeyboardMarkup
import net.romangr.partybot.client.telegram.callbacks.CallbackDataFactory
import net.romangr.partybot.party.PartyDto
import net.romangr.partybot.party.UserDto

object PartyView {

    fun buildPartyMessage(party: PartyDto): String {
        val participants = ArrayList<UserDto>()
        var emptySeats = 0
        for (seat in party.seats) {
            if (seat.user == null) {
                emptySeats++
                continue
            }
            participants.add(seat.user)
        }
        val messageStart =
            if (emptySeats > 0) "There is a room for $emptySeats more" else "The party is full, you can join the waiting list"
        var joinedUsers = participants.joinToString("\n") { u -> markdownLinkToUserProfile(u) }
        if (joinedUsers.isBlank()) {
            joinedUsers = "Be the first to join!".escapeMarkdownCharacters()
        }

        val minimalMessage = "${messageStart.escapeMarkdownCharacters()}\n\nParticipating:\n$joinedUsers"

        if (party.queue.isNotEmpty()) {
            val queueParticipants = party.queue.joinToString("\n") { p -> markdownLinkToUserProfile(p.user) }
            return "$minimalMessage\n\nWaiting list:\n$queueParticipants"
        }
        return minimalMessage
    }

    fun buildPartyInlineKeyboard(partyId: Long) = InlineKeyboardMarkup(
        listOf(
            listOf(
                InlineKeyboardButton(text = "I'm in!", callbackData = CallbackDataFactory.joiningCallbackData(partyId)),
                InlineKeyboardButton(
                    text = "Not joining",
                    callbackData = CallbackDataFactory.passingCallbackData(partyId)
                )
            )
        )
    )

    fun markdownUserTag(user: UserDto): String {
        val at = if (user.telegramUsername.isNullOrBlank()) "" else "@"
        val text = (user.telegramUsername ?: user.name).escapeMarkdownCharacters()
        return """[$at$text](tg://user?id=${user.telegramId})"""
    }

    private fun markdownLinkToUserProfile(user: UserDto): String {
        val username = user.telegramUsername?.takeIf { it != user.name }?.let { "($it)" }
        val text = listOfNotNull(user.name, username).joinToString(" ").escapeMarkdownCharacters()
        return """[$text](tg://user?id=${user.telegramId})"""
    }
}

fun String.escapeMarkdownCharacters(): String = this
//    .replace("_", """\_""")
    .replace("*", """\*""")
    .replace("`", """\`""")
    .replace("[", """\[""")