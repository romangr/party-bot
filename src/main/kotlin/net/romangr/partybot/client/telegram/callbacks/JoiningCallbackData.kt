package net.romangr.partybot.client.telegram.callbacks

import com.elbekd.bot.Bot
import com.elbekd.bot.model.ChatId
import com.elbekd.bot.types.ParseMode
import net.romangr.partybot.client.telegram.views.PartyView
import net.romangr.partybot.party.PartyJoiningStatus
import net.romangr.partybot.party.PartyService
import net.romangr.partybot.party.UnknownUser
import org.slf4j.LoggerFactory

class JoiningCallbackData(private val partyId: Long, private val partyService: PartyService) : CallbackData {

    override suspend fun execute(bot: Bot, user: UnknownUser, queryId: String) {
        val partyJoiningResult = partyService.joinParty(partyId, user)
        val message = when (partyJoiningResult.status) {
            PartyJoiningStatus.SUCCESS -> "You have joined the party!"
            PartyJoiningStatus.CANT_CREATE_USER -> "Something went wrong, try again later"
            PartyJoiningStatus.UNEXPECTED_ERROR -> "Something went wrong, try again later"
            PartyJoiningStatus.NO_AVAILABLE_SEATS -> "The party is full, you have joined the waiting list"
            PartyJoiningStatus.ALREADY_IN_THE_QUEUE -> "You are already in the waiting list"
            PartyJoiningStatus.ALREADY_JOINED -> "You have already joined this party!"
        }
        bot.answerCallbackQuery(queryId, message, showAlert = true)
        val party = partyService.getParty(partyId)
        if (party?.messageId == null) {
            logger.warn("Can't update party message. Can't retrieve the party or message id is null: {}", party)
            return
        }
        bot.editMessageText(
            ChatId.IntegerId(party.chatId),
            party.messageId,
            text = PartyView.buildPartyMessage(party),
            replyMarkup = PartyView.buildPartyInlineKeyboard(partyId),
            parseMode = ParseMode.Markdown
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JoiningCallbackData::class.java)
    }
}