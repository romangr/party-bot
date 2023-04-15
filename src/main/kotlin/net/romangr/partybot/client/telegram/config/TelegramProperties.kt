package net.romangr.partybot.client.telegram.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "telegram")
class TelegramProperties {

    lateinit var botUsername: String
    lateinit var token: String
}
