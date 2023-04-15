package net.romangr.partybot.client.telegram.config

import com.elbekd.bot.Bot
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TelegramBotConfig {

    @Bean
    fun telegramBot(telegramProperties: TelegramProperties): Bot =
        Bot.createPolling(username = telegramProperties.botUsername, token = telegramProperties.token)
}
