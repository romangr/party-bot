package net.romangr.partybot.users

import net.romangr.partybot.party.UnknownUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun createUserFromTelegram(user: UnknownUser) =
        userRepository.createUserFromTelegram(user.telegramId, user.name, user.telegramUsername)

    fun findUserByTelegramId(telegramId: Long) = this.userRepository.findUserByTelegramId(telegramId)

    fun updateUser(userId: Int, user: UnknownUser): User {
        val isUpdateSuccessful = this.userRepository.updateUserData(userId, user.name, user.telegramUsername)
        val updatedUser = this.userRepository.findUserByTelegramId(user.telegramId)
        if (!isUpdateSuccessful || updatedUser == null) {
            logger.warn("Couldn't update user with id {} or user ({}) is null after update", userId, updatedUser)
            return User(
                id = userId,
                name = user.name,
                telegramId = user.telegramId,
                telegramUsername = user.telegramUsername
            )
        }
        return updatedUser
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserService::class.java)
    }

}