package net.romangr.partybot.client.telegram.callbacks

import com.elbekd.bot.Bot
import net.romangr.partybot.party.PartyService
import net.romangr.partybot.party.UnknownUser
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class CallbackDataFactory(private val partyService: PartyService) {

    private val callbackDataCreators = mapOf<CallbackType, (List<String>) -> CallbackData>(
        Pair(CallbackType.JOINING) { elements: List<String> ->
            JoiningCallbackData(partyId = elements[1].toLong(), partyService)
        },
        Pair(CallbackType.PASSING) { elements: List<String> ->
            PassingCallbackData(partyId = elements[1].toLong(), partyService)
        },
    )

    fun parse(callbackString: String): CallbackData {
        if (callbackString.isBlank()) {
            throw CallbackDataParsingException("Callback string is blank")
        }
        val splitCallback: List<String> = callbackString.split(delimiter)
        val callbackType = CallbackType.parse(splitCallback[0])
            ?: throw CallbackDataParsingException("Unknown callback type '${splitCallback[0]}'")
        return callbackDataCreators[callbackType]?.invoke(splitCallback)
            ?: throw CallbackDataParsingException("No creator for callback type '$callbackType'")
    }

    companion object {
        const val delimiter = ":"

        private fun randomString(): String = Random.Default.nextInt(0, 1000000).toString()

        fun joiningCallbackData(partyId: Long): String =
            arrayListOf(CallbackType.JOINING.prefix, partyId, randomString())
                .joinToString(delimiter)

        fun passingCallbackData(partyId: Long): String =
            arrayListOf(CallbackType.PASSING.prefix, partyId, randomString())
                .joinToString(delimiter)
    }

}

interface CallbackData {

    suspend fun execute(bot: Bot, user: UnknownUser, queryId: String)

}

enum class CallbackType(val prefix: String) {
    JOINING("J"),
    PASSING("P");

    companion object Parser {
        private val typeByPrefix: Map<String, CallbackType> = values()
            .map { Pair(it.prefix, it) }
            .toMap()

        fun parse(prefix: String): CallbackType? = typeByPrefix[prefix]
    }
}

class CallbackDataParsingException(message: String) : RuntimeException(message)
