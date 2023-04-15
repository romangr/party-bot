package net.romangr.partybot

import com.elbekd.bot.Bot
import kotlinx.serialization.json.Json
import net.romangr.partybot.party.PartyCreationStatus
import net.romangr.partybot.party.PartyDto
import net.romangr.partybot.party.PartyService
import net.romangr.partybot.party.UnknownUser
import net.romangr.partybot.users.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@Tag("multi-thread")
@SpringBootTest
@Testcontainers
class PartyBotMultithreadTests {

    @Autowired
    lateinit var userService: UserService

    @Autowired
    lateinit var partyService: PartyService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    lateinit var bot: Bot

    @Test
    fun createPartyAndJoin() {
        val users = (1..100).map { n -> UnknownUser("Username$n", n.toLong(), null) }
        users.forEach { userService.createUserFromTelegram(it) }
        val partyCreationResult = partyService.createParty(users[0], 1, 10)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value
        assertThat(partyId).isNotNull()
        val latch = CountDownLatch(101)
        val leaveCounter = AtomicInteger(0)
        val removedUsers = ConcurrentSkipListSet<Long>()


        val threads = users.mapIndexed { i, u ->
            object : Thread() {
                override fun run() {
                    println("Thread ${this.name} ready for user $i")
                    latch.countDown()
                    latch.await()
                    partyService.joinParty(partyId!!, u)
                    if (Random.Default.nextDouble() > 0.5) {
                        val count = leaveCounter.incrementAndGet()
                        if (count < 20) {
                            removedUsers.add(u.telegramId)
                            partyService.leaveParty(partyId, u)
                        }
                    }
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join() }

        val removedUserIds = removedUsers.map { userService.findUserByTelegramId(it) }.map { it!!.id }
        val party = partyService.getParty(partyId!!)
        println(Json.encodeToString(PartyDto.serializer(), party!!))
        assertThat(party.seats).hasSize(10)
        assertThat(party.seats.filter { s -> s.user == null }).hasSize(0)
        val set = HashSet<Int>()
        val idsOfUsersInParty = party.seats.map { s -> s.user!!.id }
        val idsOfUsersInQueue = party.queue.map { q -> q.user.id }
        set.addAll(idsOfUsersInParty)
        set.addAll(idsOfUsersInQueue)
        assertThat(set).hasSize(idsOfUsersInParty.size + idsOfUsersInQueue.size)
        assertThat(set).doesNotContainAnyElementsOf(removedUserIds)
        assertThat(party.queue).hasSize(90 - removedUserIds.size)
    }
}
