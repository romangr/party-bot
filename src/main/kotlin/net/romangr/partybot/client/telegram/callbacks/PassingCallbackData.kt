package net.romangr.partybot.client.telegram.callbacks

import com.elbekd.bot.Bot
import com.elbekd.bot.model.ChatId
import com.elbekd.bot.types.ParseMode
import net.romangr.partybot.client.telegram.views.PartyView
import net.romangr.partybot.client.telegram.views.PartyView.markdownUserTag
import net.romangr.partybot.party.PartyLeavingStatus
import net.romangr.partybot.party.PartyService
import net.romangr.partybot.party.UnknownUser
import org.slf4j.LoggerFactory

class PassingCallbackData(private val partyId: Long, private val partyService: PartyService) : CallbackData {
    override suspend fun execute(bot: Bot, user: UnknownUser, queryId: String) {
        logger.debug("Executing passing callback for user {}", user.telegramId)
        val partyLeavingResult = partyService.leaveParty(partyId, user)

        val message = when (partyLeavingResult.status) {
            PartyLeavingStatus.SUCCESS -> "You have been excluded from the party"
            PartyLeavingStatus.NOT_IN_THE_PARTY -> "You are not in the party"
            PartyLeavingStatus.CANT_RETRIEVE_USER -> "Something went wrong, try again later"
            PartyLeavingStatus.UNKNOWN_ERROR -> "Something went wrong, try again later"
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
        val propagatedUserId = partyLeavingResult.value.propagatedUser
        if (propagatedUserId != null) {
            val propagatedUser = party.seats.find { s -> s.user?.id == propagatedUserId }?.user
            if (propagatedUser == null) {
                logger.warn("Can't find propagated user {} in the seats list for party {}", propagatedUserId, partyId)
                return
            }
            val propagationMessage =
                """${markdownUserTag(propagatedUser)} someone has left the party and now you are in the participants list. If you are not going to participate, please press "Not joining" """
            bot.sendMessage(ChatId.IntegerId(party.chatId), propagationMessage, ParseMode.Markdown, replyToMessageId = party.messageId)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JoiningCallbackData::class.java)
    }
}