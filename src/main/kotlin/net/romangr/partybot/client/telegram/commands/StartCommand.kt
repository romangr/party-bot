package net.romangr.partybot.client.telegram.commands

import com.elbekd.bot.Bot
import com.elbekd.bot.model.toChatId
import com.elbekd.bot.types.Message
import net.romangr.partybot.client.telegram.mappers.UserMapper
import net.romangr.partybot.users.UserService
import org.springframework.stereotype.Component


@Component
class StartCommand(
    private val bot: Bot,
    private val userService: UserService
) : TelegramCommand {

    override fun command(): String = "/start"

    override suspend fun action(input: Pair<Message, String?>) {
        val (message) = input
        val from = message.from
        val senderId = from?.id
        if (senderId === null) {
            return
        }
        val user = UserMapper.fromTelegramUser(from)
        userService.createUserFromTelegram(user)
        val welcomeMessage =
"""Hello ${user.name}!

This is a bot that can help you to unite a group of people for some activity with limited spots available.
"""
        bot.sendMessage(message.chat.id.toChatId(), welcomeMessage)
    }

}
