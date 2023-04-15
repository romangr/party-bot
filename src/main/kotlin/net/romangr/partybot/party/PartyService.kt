package net.romangr.partybot.party

import kotlinx.datetime.Clock
import net.romangr.partybot.ActionResult
import net.romangr.partybot.users.User
import net.romangr.partybot.users.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val userService: UserService,
    txnManager: PlatformTransactionManager
) {
    private val txn: TransactionTemplate

    init {
        this.txn = TransactionTemplate(txnManager)
        this.txn.isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    fun createParty(owner: UnknownUser, chatId: Long, seats: Int): ActionResult<Long?, PartyCreationStatus> {
        if (seats > 50) {
            return ActionResult(null, PartyCreationStatus.TOO_MANY_SEATS)
        }
        val internalUser = getInternalUser(owner) ?: return ActionResult(null, PartyCreationStatus.CANT_CREATE_USER)
        return partyRepository.createParty(internalUser.id.toLong(), chatId, seats)
    }

    fun getParty(partyId: Long): PartyDto? {
        return partyRepository.getParty(partyId)
    }

    fun joinParty(partyId: Long, user: UnknownUser): ActionResult<Unit, PartyJoiningStatus> {
        val internalUser = getInternalUser(user) ?: return ActionResult(Unit, PartyJoiningStatus.CANT_CREATE_USER)
        val userId = internalUser.id

        val result = runCatching {
            txn.execute { transaction ->
                val queueParticipants = partyRepository.retrieveQueueParticipants(partyId, true)

                if (queueParticipants.find { p -> p.userId == userId } != null) {
                    logger.debug("User {} is already in the queue for party {}", userId, partyId)
                    return@execute ActionResult(Unit, PartyJoiningStatus.ALREADY_IN_THE_QUEUE)
                }

                if (partyRepository.isUserOnTheSeat(partyId, userId)) {
                    return@execute ActionResult(Unit, PartyJoiningStatus.ALREADY_JOINED)
                }

                val isSeatTaken = partyRepository.tryToTakeASeat(userId, partyId)

                if (!isSeatTaken) {
                    logger.debug("Seat hasn't been taken")
                    val participant = QueueParticipant(userId, Clock.System.now())
                    partyRepository.addUserToQueue(participant, partyId)
                    return@execute ActionResult(Unit, PartyJoiningStatus.NO_AVAILABLE_SEATS)
                }
                ActionResult(Unit, PartyJoiningStatus.SUCCESS)
            }
        }

        if (result.isFailure) {
            val exception = result.exceptionOrNull();
            if (exception == null) {
                logger.warn("Party joining transaction failed for some unexpected reason");
                return ActionResult(Unit, PartyJoiningStatus.UNEXPECTED_ERROR)
            }
            logger.warn("Party joining transaction failed for some unexpected reason", exception)
            return ActionResult(Unit, PartyJoiningStatus.UNEXPECTED_ERROR)
        }

        val transactionResult = result.getOrNull()
        if (transactionResult == null) {
            logger.warn("Party joining transaction was not completed for some unexpected reason");
            return ActionResult(Unit, PartyJoiningStatus.UNEXPECTED_ERROR)
        }
        return transactionResult
    }

    fun setPartyMessageId(partyId: Long, messageId: Long) {
        logger.debug("Updating party message id for party {}, message id {}", partyId, messageId)
        partyRepository.setPartyMessageId(partyId, messageId)
    }

    fun leaveParty(partyId: Long, user: UnknownUser): ActionResult<LeavePartyResult, PartyLeavingStatus> {
        logger.debug("User {} leaving from the party {}", user, partyId)
        val internalUser =
            getInternalUser(user) ?: return ActionResult(LeavePartyResult(null), PartyLeavingStatus.CANT_RETRIEVE_USER)
        return leavePartyInternal(partyId, internalUser.id)
    }

    private fun leavePartyInternal(partyId: Long, userId: Int): ActionResult<LeavePartyResult, PartyLeavingStatus> {
        val result: Result<ActionResult<LeavePartyResult, PartyLeavingStatus>?> = runCatching {
            txn.execute { transaction ->
                val queueParticipants = partyRepository.retrieveQueueParticipants(partyId, true)
                val indexOfUser = queueParticipants.indexOfFirst { p -> p.userId == userId }

                if (indexOfUser != -1) {
                    logger.debug("User {} will be removed from the queue", userId)
                    val isUpdateSuccessful = partyRepository.removeParticipantFromQueue(indexOfUser, partyId)
                    if (!isUpdateSuccessful) {
                        logger.warn("Couldn't remove a participant from the queue for party {}", partyId)
                        transaction.setRollbackOnly()
                        return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
                    }
                    return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.SUCCESS)
                }

                val isSeatFound = partyRepository.findUserSeatAndLock(partyId, userId)

                if (!isSeatFound) {
                    logger.debug("User {} is not in the party {}", userId, partyId)
                    return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.NOT_IN_THE_PARTY)
                }

                if (queueParticipants.isEmpty()) {
                    logger.debug("Queue for party {} is empty, emptying the seat", partyId)
                    val isUpdateSuccessful = partyRepository.assignSeatToUser(partyId, userId, null)
                    if (!isUpdateSuccessful) {
                        logger.warn("Couldn't remove a participant {} from the seat for party {}", userId, partyId)
                        transaction.setRollbackOnly()
                        return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
                    }
                    return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.SUCCESS)
                }

                val isRemovalSuccessful = partyRepository.removeParticipantFromQueue(0, partyId)
                if (!isRemovalSuccessful) {
                    logger.warn("Couldn't remove a participant from the queue for party {}", partyId)
                    transaction.setRollbackOnly()
                    return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
                }
                val userToPropagate = queueParticipants[0].userId
                val isAssignmentSuccessful = partyRepository.assignSeatToUser(partyId, userId, userToPropagate)
                if (!isAssignmentSuccessful) {
                    logger.warn("Couldn't remove a participant {} from the seat for party {}", userId, partyId)
                    transaction.setRollbackOnly()
                    return@execute ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
                }
                logger.debug("Propagated user {} to the seat of party {}", userToPropagate, partyId)
                return@execute ActionResult(
                    LeavePartyResult(propagatedUser = userToPropagate),
                    PartyLeavingStatus.SUCCESS
                )
            }
        }
        if (result.isFailure) {
            val exception = result.exceptionOrNull();
            if (exception == null) {
                logger.warn("Party joining transaction failed for some unexpected reason");
                return ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
            }
            logger.warn("Party joining transaction failed for some unexpected reason", exception)
            return ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
        }
        return result.getOrNull() ?: ActionResult(LeavePartyResult(), PartyLeavingStatus.UNKNOWN_ERROR)
    }

    private fun getInternalUser(user: UnknownUser): User? {
        var internalUser = userService.findUserByTelegramId(user.telegramId)
        if (internalUser == null) {
            val userCreationResult = userService.createUserFromTelegram(user)
            if (userCreationResult == null || !userCreationResult.success) {
                logger.warn(
                    "Couldn't create a user from telegram {} with error code {}",
                    user,
                    userCreationResult?.errorCode
                )
                return null
            }
            internalUser = userService.findUserByTelegramId(user.telegramId)
        }
        if (internalUser == null) {
            logger.warn("Couldn't retrieve a user {} although it should be already created", user)
            return null
        }
        if (internalUser.name != user.name || internalUser.telegramUsername != user.telegramUsername) {
            userService.updateUser(internalUser.id, user)
        }
        return internalUser
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PartyService::class.java)
    }
}

data class UnknownUser(val name: String, val telegramId: Long, val telegramUsername: String? = null)
