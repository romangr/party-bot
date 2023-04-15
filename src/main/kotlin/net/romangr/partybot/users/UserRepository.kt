package net.romangr.partybot.users

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Types

@Repository
class UserRepository(private val jdbc: JdbcTemplate, txnManager: PlatformTransactionManager) {
    private val txn: TransactionTemplate

    init {
        this.txn = TransactionTemplate(txnManager)
        this.txn.isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
    }

    fun createUserFromTelegram(telegramId: Long, name: String, telegramUsername: String?) = txn.execute {
        try {
            jdbc.update {
                val ps =
                    it.prepareStatement("INSERT INTO users (name, telegram_id, telegram_username) VALUES (?, ?, ?)")
                ps.setString(1, name)
                ps.setLong(2, telegramId)
                telegramUsername?.also { ps.setString(3, telegramUsername) } ?: ps.setNull(3, Types.VARCHAR)
                ps
            }
            return@execute UserCreationResult(success = true)
        } catch (e: DuplicateKeyException) {
            return@execute UserCreationResult(success = true, errorCode = UserCreationErrorCode.USER_ALREADY_EXISTS)
        } catch (e: Throwable) {
            logger.error("Error creating a new user", e)
            return@execute UserCreationResult(success = false, errorCode = UserCreationErrorCode.UNKNOWN_ERROR)
        }
    }

    fun findUserByTelegramId(telegramId: Long): User? {
        runCatching {
            jdbc.queryForMap("SELECT * FROM users WHERE telegram_id = $telegramId")
        }.onSuccess { user ->
            return User(
                id = user["id"] as Int,
                name = user["name"] as String,
                telegramId = user["telegram_id"] as Long,
                telegramUsername = user["telegram_username"] as String?
            )
        }.onFailure { e ->
            if (e !is EmptyResultDataAccessException) {
                logger.warn("Error retrieving user by telegram id {}", telegramId, e)
            }
            return null
        }
        throw RuntimeException("Unexpected state")
    }

    fun updateUserData(userId: Int, name: String, telegramUsername: String?): Boolean {
        val result = txn.execute {
            jdbc.update(
                "UPDATE users SET name = ?, telegram_username = ? WHERE id = ?",
                name,
                telegramUsername,
                userId
            )
        }
        return result == 1
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserRepository::class.java)
    }

}

data class User(
    val id: Int,
    val name: String,
    val telegramId: Long,
    val telegramUsername: String? = null
)

data class UserCreationResult(val success: Boolean, val errorCode: UserCreationErrorCode? = null)

enum class UserCreationErrorCode {
    USER_ALREADY_EXISTS,
    UNKNOWN_ERROR
}