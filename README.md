# Party bot 
![build status](https://github.com/romangr/party-bot/actions/workflows/graalvm.yml/badge.svg)

This bot is created to simplify the process of building a party with a limited number of participants.
For example, you are going to play basketball and you need to find 10 people who are ready to join and notify
the others that there are no slots available.

This can be done with the bot's command `/party10` or `/party 10`

The bot should be added to the group chat to allow others to participate.

## Mechanics

1. Create a new party with the `/party` command
2. New participants can join using "I'm in" button
3. When the party is full, all the next joiners are added to the waiting list
4. When someone from the party leaves using "Not joining" button, the first participant from the waiting list is added to the main list. Also, a notification about this action is sent to the chat.

## Running the bot

The bot requires a PostgreSQL instance. You can use a `docker-compose.yml` file provided in the root of the repo.
To use the image from Docker Packages you can follow this [manual](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic).

There are required environment variables:

* DB_PASSWORD - password for PostgreSQL (if you run it first time, this password will be set for the created instance and passed to the bot to use it for connection)
* TELEGRAM_BOT_TOKEN - token for Telegram bot from BotFather bot
* TELEGRAM_BOT_USERNAME - username of your Telegram bot (without @ at the beginning) to handle command like `/party10@username` properly