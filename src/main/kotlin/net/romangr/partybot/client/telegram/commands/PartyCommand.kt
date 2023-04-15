package net.romangr.partybot.client.telegram.commands

import com.elbekd.bot.Bot
import com.elbekd.bot.model.toChatId
import com.elbekd.bot.types.Message
import com.elbekd.bot.types.ParseMode
import net.romangr.partybot.client.telegram.config.TelegramProperties
import net.romangr.partybot.client.telegram.mappers.UserMapper
import net.romangr.partybot.client.telegram.views.PartyView
import net.romangr.partybot.party.PartyCreationStatus
import net.romangr.partybot.party.PartyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PartyCommand(
    private val bot: Bot, private val partyService: PartyService, private val properties: TelegramProperties
) : TelegramCommand {

    override fun command(): String = "/party"

    override suspend fun action(input: Pair<Message, String?>) {
        val (message) = input
        val text = input.first.text ?: return

        val numberOfParticipants =
            parsePartyCommand(argumentCommandPattern, text) ?: parsePartyCommand(joinedCommandPattern, text)
        if (numberOfParticipants == null) {
            sendPartySyntaxHelp(message)
            return
        }

        val from = message.from
        val senderId = from?.id
        if (senderId === null) {
            return
        }

        val partyCreationStatusActionResult =
            partyService.createParty(UserMapper.fromTelegramUser(from), message.chat.id, numberOfParticipants)

        if (partyCreationStatusActionResult.status == PartyCreationStatus.TOO_MANY_SEATS) {
            bot.sendMessage(
                message.chat.id.toChatId(),
                "Parties with more than 50 participants are supported with Premium subscription only"
            )
            return
        }

        if (partyCreationStatusActionResult.status != PartyCreationStatus.SUCCESS || partyCreationStatusActionResult.value == null) {
            sendMessageAboutServiceUnavailability(message)
            return
        }

        val party = partyService.getParty(partyCreationStatusActionResult.value)
        if (party == null) {
            logger.warn("Can't find party ({}) by id after creation", partyCreationStatusActionResult.value)
            sendMessageAboutServiceUnavailability(message)
            return
        }


        val sentMessage = bot.sendMessage(
            message.chat.id.toChatId(),
            PartyView.buildPartyMessage(party),
            replyMarkup = PartyView.buildPartyInlineKeyboard(party.id),
            parseMode = ParseMode.Markdown
        )

        partyService.setPartyMessageId(party.id, sentMessage.messageId)
    }


    private suspend fun sendPartySyntaxHelp(message: Message) {
        bot.sendMessage(
            message.chat.id.toChatId(),
            """Seems like you'd like to use /party command. The syntax is the following:
                        | /party <number of participants>
                        | for example, /party 10
                        | You also can use a version without a whitespace: /party15
                    """.trimMargin()
        )
    }

    private suspend fun sendMessageAboutServiceUnavailability(message: Message) {
        bot.sendMessage(
            message.chat.id.toChatId(), "We are experiencing some issue at the moment, please try again later!"
        )
    }

    fun parsePartyCommand(pattern: Regex, text: String) =
        pattern.matchEntire(text.replace("@${properties.botUsername}", ""))
            ?.let { it.groupValues[1].toIntOrNull() }

    companion object {
        private val logger = LoggerFactory.getLogger(PartyCommand::class.java)
        private val argumentCommandPattern = Regex("/party (\\d+)")
        val joinedCommandPattern = Regex("/party(\\d+)")
    }

}
