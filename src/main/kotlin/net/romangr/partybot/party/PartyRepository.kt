package net.romangr.partybot.party

import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json
import net.romangr.partybot.ActionResult
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Statement
import java.sql.Types


@OptIn(ExperimentalSerializationApi::class)
@Repository
class PartyRepository(private val jdbc: JdbcTemplate, txnManager: PlatformTransactionManager) {
    private val txn: TransactionTemplate

    init {
        this.txn = TransactionTemplate(txnManager)
        this.txn.isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    fun createParty(ownerId: Long, chatId: Long, seatsNumber: Int): ActionResult<Long?, PartyCreationStatus> {
        val result: ActionResult<Long?, PartyCreationStatus>? = txn.execute { transaction ->
            logger.debug("Creating a new party. Running in a new transaction = {}", transaction.isNewTransaction)
            val partyKeyHolder = GeneratedKeyHolder()
            val updatedPartyRows = jdbc.update(
                {
                    val ps = it.prepareStatement(
                        "INSERT INTO parties (owner_id, chat_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS
                    )
                    ps.setLong(1, ownerId)
                    ps.setLong(2, chatId)
                    ps
                }, partyKeyHolder
            )
            val partyInsertGeneratedKeys = partyKeyHolder.keys
            logger.debug("Updated {} rows, generated keys {}", updatedPartyRows, partyInsertGeneratedKeys)
            if (updatedPartyRows != 1 || partyInsertGeneratedKeys == null) {
                logger.warn("Party was not created for some unexpected reason")
                transaction.setRollbackOnly()
                return@execute ActionResult(null, PartyCreationStatus.UNKNOWN_ERROR)
            }
            val partyId = partyInsertGeneratedKeys["ID"] ?: partyInsertGeneratedKeys["id"]
            val partyParameters = (1..seatsNumber).map { PartyParameters(partyId as Long, it) }

            jdbc.batchUpdate(
                "INSERT INTO seats (party_id, internal_number) VALUES (?, ?)", partyParameters, 100
            ) { ps, params ->
                ps.setLong(1, params.partyId)
                ps.setInt(2, params.internalNumber)
            }

            val updatedQueueRows = jdbc.update(
                {
                    val ps = it.prepareStatement(
                        "INSERT INTO queues (party_id, participants) VALUES (?, ?)"
                    )
                    ps.setLong(1, partyId as Long)
                    val jsonObject = pgJsonObject("[]")
                    ps.setObject(2, jsonObject)
                    ps
                }, partyKeyHolder
            )
            if (updatedQueueRows != 1) {
                logger.warn("Queue for party was not created for some unexpected reason")
                transaction.setRollbackOnly()
                return@execute ActionResult(null, PartyCreationStatus.UNKNOWN_ERROR)
            }

            return@execute ActionResult(partyId as Long, PartyCreationStatus.SUCCESS)
        }
        if (result == null) {
            logger.warn("Party creation transaction was not completed for some unexpected reason");
            return ActionResult(null, PartyCreationStatus.UNKNOWN_ERROR)
        }
        return result
    }

    fun getParty(partyId: Long): PartyDto? {
        val partyRetrievalResult = queryForParty(partyId)

        if (partyRetrievalResult.isFailure) {
            logger.warn("Couldn't find the party by id {}", partyId, partyRetrievalResult.exceptionOrNull())
            return null
        }

        val party = partyRetrievalResult.getOrNull()!!
        val seats = queryForSeats(partyId)
        val queueParticipants = retrieveQueueParticipants(partyId, false)
        val queueParticipantUsers: Map<Int, UserDto> =
            retrieveUsers(queueParticipants.map { p -> p.userId })
                .associateBy { u -> u.id }

        return PartyDto(id = partyId, owner = UserDto(
            id = party["owner_id"] as Int,
            name = party["owner_name"] as String,
            telegramId = party["owner_telegram_id"] as Long,
            telegramUsername = party["owner_telegram_username"] as String?
        ), chatId = party["chat_id"] as Long,
            messageId = party["message_id"] as Long?,
            seats = seats.map { m ->
                SeatDto(
                    internalNumber = m["internal_number"] as Int,
                    user =
                    if (m["user_id"] == null) null
                    else UserDto(
                        id = m["user_id"] as Int,
                        name = m["user_name"] as String,
                        telegramId = m["user_telegram_id"] as Long,
                        telegramUsername = m["user_telegram_username"] as String?
                    )
                )
            },
            queue = queueParticipants.map { p ->
                QueueParticipantDto(
                    user = queueParticipantUsers[p.userId]!!,
                    addedAt = p.addedAt
                )
            })
    }

    private fun retrieveUsers(ids: List<Int>): List<UserDto> {
        val result = ArrayList<UserDto>(ids.size)
        jdbc.query({
            val ps = it.prepareStatement("SELECT id, name, telegram_id, telegram_username FROM users WHERE id = ANY (?)")
            ps.setArray(1, it.createArrayOf("INTEGER", ids.toTypedArray()))
            ps
        }, {
            result.add(
                UserDto(
                    id = it.getInt("id"),
                    name = it.getString("name"),
                    telegramId = it.getLong("telegram_id"),
                    telegramUsername = it.getString("telegram_username")
                )
            )
        })
        return result
    }

    private fun queryForSeats(partyId: Long): MutableList<MutableMap<String, Any>> =
        jdbc.queryForList(
            """
    SELECT
      s.internal_number as internal_number,
      u.id as user_id,
      u.name as user_name,
      u.telegram_id as user_telegram_id,
      u.telegram_username as user_telegram_username
    FROM seats s left join users u on s.user_id = u.id 
    where party_id = ?
    """.trimIndent(), partyId
        )

    private fun queryForParty(partyId: Long) = runCatching {
        jdbc.queryForMap(
            """
    SELECT
        p.id as party_id,
        p.chat_id as chat_id,
        p.message_id as message_id,
        u.id as owner_id,
        u.name as owner_name,
        u.telegram_id as owner_telegram_id,
        u.telegram_username as owner_telegram_username
    FROM parties p join users u on p.owner_id = u.id
    where p.id = ?
    """.trimIndent(), partyId
        )
    }

    fun addUserToQueue(participant: QueueParticipant, partyId: Long) {
        jdbc.update { connection ->
            val ps = connection.prepareStatement(
                """
                            UPDATE queues
                            SET participants = queues.participants || ?
                            WHERE party_id = ?;
                        """.trimIndent()
            )
            ps.setObject(1, pgJsonObject(Json.encodeToString(QueueParticipant.serializer(), participant)))
            ps.setLong(2, partyId)
            ps
        }
    }

    fun tryToTakeASeat(userId: Int, partyId: Long): Boolean {
        val updateResult = jdbc.update { connection ->
            val ps = connection.prepareStatement(
                """
                                UPDATE seats
                                SET user_id = ?
                                FROM (
                                    SELECT party_id, internal_number FROM seats WHERE user_id IS NULL AND party_id = ?
                                    OFFSET 0 ROWS
                                    FETCH NEXT 1 ROWS ONLY
                                    FOR UPDATE
                                ) AS s
                                WHERE seats.party_id = s.party_id AND seats.internal_number = s.internal_number
                            """.trimIndent()
            )
            ps.setInt(1, userId)
            ps.setLong(2, partyId)
            ps
        }
        return updateResult == 1
    }

    fun isUserOnTheSeat(partyId: Long, userId: Int): Boolean {
        val countQueryResult =
            jdbc.queryForMap("SELECT count(*) as count FROM seats WHERE party_id = ? AND user_id = ?", partyId, userId)
        val userCountInSeats = countQueryResult["count"] as Long
        logger.debug("User count query from seats resulted in non-zero value {}", userCountInSeats)
        return userCountInSeats == 1L
    }

    fun retrieveQueueParticipants(partyId: Long, forUpdate: Boolean): Array<QueueParticipant> {
        val queue = jdbc.queryForMap(
            "SELECT participants FROM queues WHERE party_id = ?" + if (forUpdate) " FOR UPDATE" else "",
            partyId
        )
        val queueParticipantsObject = queue["participants"] as PGobject
        val participantsObjectValue = queueParticipantsObject.value
            ?: throw RuntimeException("Queue participants object value is null for party $partyId")
        val queueParticipants = decodeQueueParticipantsArray(participantsObjectValue)
        logger.debug("There are {} participants in the queue", queueParticipants.size)
        return queueParticipants
    }

    private fun decodeQueueParticipantsArray(participantsObjectValue: String) = Json.decodeFromString(
        ArraySerializer<QueueParticipant, QueueParticipant>(QueueParticipant.serializer()),
        participantsObjectValue
    )

    fun setPartyMessageId(partyId: Long, messageId: Long) {
        val updateResult = jdbc.update { connection ->
            val ps =
                connection.prepareStatement("UPDATE parties SET message_id = ? WHERE id = ?")
            ps.setLong(1, messageId)
            ps.setLong(2, partyId)
            ps
        }
        if (updateResult != 1) {
            logger.warn("Couldn't update party message id for party {}", partyId)
        }
    }

    fun removeParticipantFromQueue(indexOfUser: Int, partyId: Long): Boolean {
        val updatedRows = jdbc.update {
            val ps =
                it.prepareStatement("UPDATE queues SET participants = participants - ? WHERE party_id = ?")
            ps.setInt(1, indexOfUser)
            ps.setLong(2, partyId)
            ps
        }
        return updatedRows == 1
    }

    fun assignSeatToUser(partyId: Long, userId: Int, newUserId: Int?): Boolean {
        val updatedRows = jdbc.update {
            val ps =
                it.prepareStatement("UPDATE seats SET user_id = ? WHERE party_id = ? AND user_id = ?")
            newUserId?.also { ps.setInt(1, newUserId) } ?: ps.setNull(1, Types.INTEGER)
            ps.setLong(2, partyId)
            ps.setInt(3, userId)
            ps
        }
        return updatedRows == 1
    }

    private fun pgJsonObject(jsonString: String): PGobject {
        val jsonObject = PGobject()
        jsonObject.type = "jsonb"
        jsonObject.value = jsonString
        return jsonObject
    }

    fun findUserSeatAndLock(partyId: Long, userId: Int): Boolean {
        val result = runCatching {
            jdbc.queryForMap(
                "SELECT internal_number FROM seats WHERE party_id = ? AND user_id = ? FOR UPDATE",
                partyId, userId
            )
        }.getOrNull()
        return result?.let { return true } ?: false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PartyRepository::class.java)
    }

}

data class PartyParameters(val partyId: Long, val internalNumber: Int)

@Table("parties")
data class Party(
    @Id val id: Int, @Column("owner_id") val ownedId: Long, @Column("message_id") val messageId: Long? = null
)

enum class PartyCreationStatus {
    SUCCESS, UNKNOWN_ERROR, CANT_CREATE_USER, TOO_MANY_SEATS
}

@Serializable
data class PartyDto(
    val id: Long,
    val owner: UserDto,
    val chatId: Long,
    val messageId: Long?,
    val seats: List<SeatDto>,
    val queue: List<QueueParticipantDto>
)

@Serializable
data class UserDto(val id: Int, val name: String, val telegramId: Long, val telegramUsername: String?)

@Serializable
data class SeatDto(val internalNumber: Int, val user: UserDto?)

@Serializable
data class QueueParticipantDto(val user: UserDto, val addedAt: Instant)

data class LeavePartyResult(val propagatedUser: Int? = null)

enum class PartyJoiningStatus {
    CANT_CREATE_USER,
    UNEXPECTED_ERROR,
    NO_AVAILABLE_SEATS,
    ALREADY_JOINED,
    ALREADY_IN_THE_QUEUE,
    SUCCESS
}

@Serializable
data class QueueParticipant(val userId: Int, val addedAt: Instant)

enum class PartyLeavingStatus {
    SUCCESS,
    NOT_IN_THE_PARTY,
    CANT_RETRIEVE_USER,
    UNKNOWN_ERROR
}