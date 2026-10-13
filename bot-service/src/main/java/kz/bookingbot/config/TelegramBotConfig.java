package kz.bookingbot.config;

import jakarta.annotation.PostConstruct;
import kz.bookingbot.bot.BookingTelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotConfig.class);

    private final BookingTelegramBot bot;

    @Value("${bot.token}")
    private String botToken;

    public TelegramBotConfig(BookingTelegramBot bot) {
        this.bot = bot;
    }

    @PostConstruct
    public void registerBot() throws Exception {
        if (botToken == null || botToken.isBlank()) {
            log.warn("BOT_TOKEN is not set — Telegram bot will not start. Set BOT_TOKEN env var to enable it.");
            return;
        }
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BotSession session = botsApi.registerBot(bot);
        log.info("Telegram bot registered, session running: {}", session.isRunning());
        bot.registerCommands();
    }
}
