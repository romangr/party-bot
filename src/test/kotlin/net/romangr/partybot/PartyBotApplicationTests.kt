package net.romangr.partybot

import com.elbekd.bot.Bot
import net.romangr.partybot.party.*
import net.romangr.partybot.users.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName


@Tag("CI")
@SpringBootTest
class PartyBotApplicationTests {

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
        val userCreationResult = userService.createUserFromTelegram(UnknownUser("Username", 1, null))
        assertThat(userCreationResult).isNotNull
        assertThat(userCreationResult?.success ?: false).isTrue()

        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1, null), 1, 10)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(10)
        assertThat(party.seats.map { s -> s.user }).containsOnly(null)

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val internalUser = userService.findUserByTelegramId(1001)
        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(10)
        assertThat(party2.seats.map { s -> s.user?.id }).contains(internalUser!!.id)
        assertThat(party2.queue).isEmpty()
    }

    @Test
    fun createPartyAndJoinTwoTimes() {
        val userCreationResult = userService.createUserFromTelegram(UnknownUser("Username", 1, null))
        assertThat(userCreationResult).isNotNull
        assertThat(userCreationResult?.success ?: false).isTrue()

        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1, null), 1, 10)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(10)
        assertThat(party.seats.map { s -> s.user }).containsOnly(null)

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.ALREADY_JOINED)

        val internalUser = userService.findUserByTelegramId(1001)
        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(10)
        val userIds = party2.seats.map { s -> s.user?.id }
        assertThat(userIds).contains(internalUser!!.id)
        assertThat(userIds.filter { id -> id == internalUser.id }).hasSize(1)
        assertThat(party2.queue).isEmpty()
    }

    @Test
    fun createPartyAndFillAllSeats() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 2)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult3 = partyService.joinParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyJoiningResult3.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val firstJoiner = userService.findUserByTelegramId(1001)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val thirdJoiner = userService.findUserByTelegramId(1003)
        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(2)
        val userIds = party2.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id, secondJoiner!!.id)
        assertThat(party2.queue).hasSize(1)
        assertThat(party2.queue.map { p -> p.user.id }).containsOnly(thirdJoiner!!.id)
    }


    @Test
    fun createPartyFillAllSeatsAndJoinQueueTwice() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 2)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult3 = partyService.joinParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyJoiningResult3.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val partyJoiningResult4 = partyService.joinParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyJoiningResult4.status).isEqualTo(PartyJoiningStatus.ALREADY_IN_THE_QUEUE)

        val firstJoiner = userService.findUserByTelegramId(1001)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val thirdJoiner = userService.findUserByTelegramId(1003)
        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(2)
        val userIds = party2.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id, secondJoiner!!.id)
        assertThat(party2.queue).hasSize(1)
        assertThat(party2.queue.map { p -> p.user.id }).containsOnly(thirdJoiner!!.id)
    }

    @Test
    fun createPartyJoinAndJoinTheQueue() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 2)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult3 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult3.status).isEqualTo(PartyJoiningStatus.ALREADY_JOINED)

        val firstJoiner = userService.findUserByTelegramId(1001)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(2)
        val userIds = party2.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id, secondJoiner!!.id)
        assertThat(party2.queue).hasSize(0)
    }

    @Test
    fun createPartyJoinAndLeave() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 3)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyLeavingResult = partyService.leaveParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyLeavingResult.status).isEqualTo(PartyLeavingStatus.SUCCESS)
        assertThat(partyLeavingResult.value.propagatedUser).isNull()

        val firstJoiner = userService.findUserByTelegramId(1001)
        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(3)
        val userIds = party.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id, null)
        assertThat(party.queue).hasSize(0)
    }

    @Test
    fun createPartyJoinQueueAndLeave() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 1)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val partyJoiningResult3 = partyService.joinParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyJoiningResult3.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val partyWithQueue = partyService.getParty(partyId)
        assertThat(partyWithQueue!!.queue).hasSize(2)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val thirdJoiner = userService.findUserByTelegramId(1003)
        assertThat(partyWithQueue.queue[0].user.id == secondJoiner!!.id)
        assertThat(partyWithQueue.queue[1].user.id).isEqualTo(thirdJoiner!!.id)

        val partyLeavingResult = partyService.leaveParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyLeavingResult.status).isEqualTo(PartyLeavingStatus.SUCCESS)
        assertThat(partyLeavingResult.value.propagatedUser).isNull()

        val firstJoiner = userService.findUserByTelegramId(1001)
        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(1)
        val userIds = party.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id)
        assertThat(party.queue).hasSize(1)
        assertThat(party.queue[0].user.id).isEqualTo(thirdJoiner.id)
    }

    @Test
    fun createPartyDontJoinAndLeave() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 3)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val firstJoiner = userService.findUserByTelegramId(1001)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(3)
        val userIds = party.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(firstJoiner!!.id, secondJoiner!!.id, null)
        assertThat(party.queue).hasSize(0)

        val partyLeavingResult = partyService.leaveParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyLeavingResult.status).isEqualTo(PartyLeavingStatus.NOT_IN_THE_PARTY)
        assertThat(partyLeavingResult.value.propagatedUser).isNull()

        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(3)
        val userIds2 = party2.seats.map { s -> s.user?.id }
        assertThat(userIds2).containsOnly(firstJoiner.id, secondJoiner.id, null)
        assertThat(party2.queue).hasSize(0)
    }

    @Test
    fun propagationFromQueue() {
        val partyCreationResult = partyService.createParty(UnknownUser("Username", 1), 1, 1)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val partyJoiningResult2 = partyService.joinParty(partyId, UnknownUser("secondJoiner", 1002))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val partyJoiningResult3 = partyService.joinParty(partyId, UnknownUser("thirdJoiner", 1003))
        assertThat(partyJoiningResult3.status).isEqualTo(PartyJoiningStatus.NO_AVAILABLE_SEATS)

        val partyWithQueue = partyService.getParty(partyId)
        assertThat(partyWithQueue!!.queue).hasSize(2)
        val secondJoiner = userService.findUserByTelegramId(1002)
        val thirdJoiner = userService.findUserByTelegramId(1003)
        assertThat(partyWithQueue.queue[0].user.id == secondJoiner!!.id)
        assertThat(partyWithQueue.queue[1].user.id == thirdJoiner!!.id)

        val partyLeavingResult = partyService.leaveParty(partyId, UnknownUser("firstJoiner", 1001))
        assertThat(partyLeavingResult.status).isEqualTo(PartyLeavingStatus.SUCCESS)
        assertThat(partyLeavingResult.value.propagatedUser).isEqualTo(secondJoiner.id)

        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(1)
        val userIds = party.seats.map { s -> s.user?.id }
        assertThat(userIds).containsOnly(secondJoiner.id)
        assertThat(party.queue).hasSize(1)
        assertThat(party.queue[0].user.id == thirdJoiner.id)
    }

    @Test
    fun userDataUpdated() {
        val user = UnknownUser("Name", 850, "Username")
        val partyCreationResult = partyService.createParty(user, 1, 1)
        assertThat(partyCreationResult.status).isEqualTo(PartyCreationStatus.SUCCESS)
        val partyId = partyCreationResult.value!!

        val partyJoiningResult = partyService.joinParty(partyId, user)
        assertThat(partyJoiningResult.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val party = partyService.getParty(partyId)
        assertThat(party).isNotNull
        assertThat(party!!.seats).hasSize(1)
        val userFromParty = party.seats[0].user
        assertThat(userFromParty).isNotNull
        assertThat(userFromParty!!.name).isEqualTo(user.name)
        assertThat(userFromParty.telegramUsername).isEqualTo(user.telegramUsername)

        val partyLeavingResult = partyService.leaveParty(partyId, user)
        assertThat(partyLeavingResult.status).isEqualTo(PartyLeavingStatus.SUCCESS)

        val partyJoiningResult2 =
            partyService.joinParty(partyId, UnknownUser("Updated name", user.telegramId, "Updated_username"))
        assertThat(partyJoiningResult2.status).isEqualTo(PartyJoiningStatus.SUCCESS)

        val party2 = partyService.getParty(partyId)
        assertThat(party2).isNotNull
        assertThat(party2!!.seats).hasSize(1)
        val userFromParty2 = party2.seats[0].user
        assertThat(userFromParty2).isNotNull
        assertThat(userFromParty2!!.name).isEqualTo("Updated name")
        assertThat(userFromParty2.telegramUsername).isEqualTo("Updated_username")
    }

    companion object {

        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:15.2"))

        init {
            postgres.start()
            System.setProperty("spring.datasource.url", postgres.jdbcUrl)
            System.setProperty("spring.datasource.username", postgres.username)
            System.setProperty("spring.datasource.password", postgres.password)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            postgres.stop()
            postgres.close()
        }
    }

}
