package kz.bookingbot.bot;

import kz.bookingbot.entity.Booking;
import kz.bookingbot.entity.BookingStatus;
import kz.bookingbot.service.BookingLifecycleService;
import kz.bookingbot.service.BookingLifecycleService.BookingStateException;
import kz.bookingbot.service.BookingNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BookingTelegramBot extends TelegramLongPollingBot implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingTelegramBot.class);
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, d MMMM 'в' HH:mm", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU);
    private static final int MAX_MESSAGE_LENGTH = 3900;

    private static final String CB_MY = "my";
    private static final String CB_CANCEL = "cx:";
    private static final String CB_ADMIN_OK = "adm:ok:";
    private static final String CB_ADMIN_NO = "adm:no:";

    private final String botToken;
    private final String botUsername;
    private final String adminChatId;
    private final String miniAppUrl;
    private final String salonName;
    private final String salonAddress;
    private final BookingLifecycleService lifecycle;

    public BookingTelegramBot(@Value("${bot.token}") String botToken,
                               @Value("${bot.username}") String botUsername,
                               @Value("${bot.admin-chat-id}") String adminChatId,
                               @Value("${app.base-url}") String appBaseUrl,
                               @Value("${bot.miniapp-path}") String miniAppPath,
                               @Value("${salon.name:Салон красоты}") String salonName,
                               @Value("${salon.address:}") String salonAddress,
                               BookingLifecycleService lifecycle) {
        super(botToken);
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.adminChatId = adminChatId == null ? "" : adminChatId.trim();
        this.miniAppUrl = appBaseUrl + miniAppPath;
        this.salonName = salonName;
        this.salonAddress = salonAddress == null ? "" : salonAddress.trim();
        this.lifecycle = lifecycle;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    private boolean isEnabled() {
        return botToken != null && !botToken.isBlank();
    }

    private boolean isAdmin(long chatId) {
        return !adminChatId.isEmpty() && adminChatId.equals(String.valueOf(chatId));
    }

    /** Регистрирует список команд в меню Telegram (для админа — расширенный). */
    public void registerCommands() {
        List<BotCommand> clientCommands = List.of(
                new BotCommand("start", "Записаться"),
                new BotCommand("my", "Мои записи"));
        safeExecute(SetMyCommands.builder()
                .commands(clientCommands)
                .scope(new BotCommandScopeDefault())
                .build());

        if (!adminChatId.isEmpty()) {
            List<BotCommand> adminCommands = new ArrayList<>(clientCommands);
            adminCommands.add(new BotCommand("today", "Записи на сегодня"));
            adminCommands.add(new BotCommand("tomorrow", "Записи на завтра"));
            safeExecute(SetMyCommands.builder()
                    .commands(adminCommands)
                    .scope(BotCommandScopeChat.builder().chatId(adminChatId).build())
                    .build());
        }
    }

    // ------------------------------------------------------------------ incoming updates

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to handle update", e);
        }
    }

    private void handleMessage(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom() != null ? message.getFrom().getId() : chatId;
        String command = message.getText().trim().split("[\\s@]")[0];

        switch (command) {
            case "/start" -> sendWelcome(chatId);
            case "/my" -> sendMyBookings(chatId, userId);
            case "/today" -> {
                if (isAdmin(chatId)) sendAgenda(chatId, LocalDate.now());
            }
            case "/tomorrow" -> {
                if (isAdmin(chatId)) sendAgenda(chatId, LocalDate.now().plusDays(1));
            }
            default -> {
                // другие сообщения игнорируем
            }
        }
    }

    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData() == null ? "" : callback.getData();
        long userId = callback.getFrom().getId();
        // Для старых (>48 ч) сообщений Telegram присылает «недоступное» сообщение без текста
        Message message = callback.getMessage() instanceof Message m ? m : null;
        long chatId = message != null ? message.getChatId() : userId;

        try {
            if (CB_MY.equals(data)) {
                answerCallback(callback, null);
                sendMyBookings(chatId, userId);
            } else if (data.startsWith(CB_CANCEL)) {
                Booking booking = lifecycle.cancelByClient(parseId(data, CB_CANCEL), userId);
                answerCallback(callback, "Запись отменена");
                editMessage(message, "❌ Запись отменена\n\n" + describe(booking));
                notifyCancelledByClient(booking);
            } else if (data.startsWith(CB_ADMIN_OK) || data.startsWith(CB_ADMIN_NO)) {
                if (!isAdmin(userId)) {
                    answerCallback(callback, "Нет доступа");
                    return;
                }
                if (data.startsWith(CB_ADMIN_OK)) {
                    Booking booking = lifecycle.confirm(parseId(data, CB_ADMIN_OK));
                    answerCallback(callback, "Запись подтверждена");
                    editMessage(message, adminCard("✅ Подтверждено", booking));
                    notifyConfirmed(booking);
                } else {
                    Booking booking = lifecycle.reject(parseId(data, CB_ADMIN_NO));
                    answerCallback(callback, "Запись отклонена");
                    editMessage(message, adminCard("🚫 Отклонено", booking));
                    notifyRejected(booking);
                }
            } else {
                answerCallback(callback, null);
            }
        } catch (BookingStateException e) {
            answerCallback(callback, e.getMessage());
            // карточка уже неактуальна — убираем кнопки
            if (message != null && message.getText() != null) {
                editMessage(message, message.getText() + "\n\nℹ️ " + e.getMessage());
            }
        }
    }

    private static Long parseId(String data, String prefix) {
        try {
            return Long.parseLong(data.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new BookingStateException("Некорректная запись");
        }
    }

    // ------------------------------------------------------------------ client flows

    private void sendWelcome(long chatId) {
        String text = "Здравствуйте! Это бот записи «" + salonName + "».\n"
                + "Нажмите «Записаться», чтобы выбрать услугу, мастера и удобное время.";
        safeExecute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard(
                        List.of(webAppButton("Записаться")),
                        List.of(callbackButton("Мои записи", CB_MY))))
                .build());
    }

    private void sendMyBookings(long chatId, long userId) {
        List<Booking> bookings = lifecycle.upcomingForUser(userId);
        if (bookings.isEmpty()) {
            safeExecute(SendMessage.builder()
                    .chatId(chatId)
                    .text("У вас нет предстоящих записей.")
                    .replyMarkup(keyboard(List.of(webAppButton("Записаться"))))
                    .build());
            return;
        }
        for (Booking booking : bookings) {
            safeExecute(SendMessage.builder()
                    .chatId(chatId)
                    .text(statusLine(booking) + "\n\n" + describe(booking))
                    .replyMarkup(keyboard(List.of(callbackButton("Отменить запись", CB_CANCEL + booking.getId()))))
                    .build());
        }
    }

    // ------------------------------------------------------------------ admin flows

    private void sendAgenda(long chatId, LocalDate day) {
        List<Booking> bookings = lifecycle.activeForDay(day);
        StringBuilder sb = new StringBuilder("📅 ").append(day.format(DAY)).append('\n');
        if (bookings.isEmpty()) {
            sb.append("\nЗаписей нет.");
        } else {
            sb.append("Записей: ").append(bookings.size()).append("\n");
            for (Booking b : bookings) {
                sb.append('\n')
                        .append(BookingStatus.PENDING.equals(b.getStatus()) ? "⏳ " : "✅ ")
                        .append(b.getSlot().getStartTime().format(TIME)).append(" · ")
                        .append(b.getSlot().getMaster().getName()).append(" · ")
                        .append(b.getService().getName()).append('\n')
                        .append("    ").append(b.getClientName()).append(", ").append(b.getClientPhone());
                if (b.getTelegramUsername() != null) {
                    sb.append(" (@").append(b.getTelegramUsername()).append(')');
                }
                sb.append('\n');
            }
            sb.append("\n⏳ — ждёт подтверждения, ✅ — подтверждена");
        }
        String text = sb.length() > MAX_MESSAGE_LENGTH ? sb.substring(0, MAX_MESSAGE_LENGTH) + "…" : sb.toString();
        safeExecute(SendMessage.builder().chatId(chatId).text(text).build());
    }

    private String adminCard(String header, Booking booking) {
        return header + " · запись #" + booking.getId() + "\n\n" + describe(booking)
                + "\n👤 " + booking.getClientName() + ", " + booking.getClientPhone()
                + (booking.getTelegramUsername() != null ? " (@" + booking.getTelegramUsername() + ")" : "");
    }

    // ------------------------------------------------------------------ BookingNotifier

    @Override
    public void notifyNewBooking(Booking booking) {
        if (!isEnabled()) {
            return;
        }
        boolean pending = BookingStatus.PENDING.equals(booking.getStatus());

        // 1. клиенту
        if (booking.getTelegramUserId() != null) {
            String header = pending
                    ? "Заявка принята ⏳\nМы подтвердим запись в ближайшее время и сразу вам напишем."
                    : "Вы записаны ✅";
            StringBuilder text = new StringBuilder(header).append("\n\n").append(describe(booking));
            appendAddress(text);
            safeExecute(SendMessage.builder()
                    .chatId(booking.getTelegramUserId())
                    .text(text.toString())
                    .replyMarkup(keyboard(List.of(callbackButton("Отменить запись", CB_CANCEL + booking.getId()))))
                    .build());
        }

        // 2. админу
        if (adminChatId.isEmpty()) {
            return;
        }
        SendMessage.SendMessageBuilder admin = SendMessage.builder()
                .chatId(adminChatId)
                .text(adminCard(pending ? "🆕 Новая запись, нужно подтверждение" : "🆕 Новая запись", booking));
        if (pending) {
            admin.replyMarkup(keyboard(List.of(
                    callbackButton("✅ Подтвердить", CB_ADMIN_OK + booking.getId()),
                    callbackButton("❌ Отклонить", CB_ADMIN_NO + booking.getId()))));
        }
        safeExecute(admin.build());
    }

    @Override
    public void notifyConfirmed(Booking booking) {
        if (!isEnabled() || booking.getTelegramUserId() == null) {
            return;
        }
        StringBuilder text = new StringBuilder("Запись подтверждена ✅\n\n").append(describe(booking));
        appendAddress(text);
        safeExecute(SendMessage.builder()
                .chatId(booking.getTelegramUserId())
                .text(text.toString())
                .replyMarkup(keyboard(List.of(callbackButton("Отменить запись", CB_CANCEL + booking.getId()))))
                .build());
    }

    @Override
    public void notifyRejected(Booking booking) {
        if (!isEnabled() || booking.getTelegramUserId() == null) {
            return;
        }
        safeExecute(SendMessage.builder()
                .chatId(booking.getTelegramUserId())
                .text("К сожалению, салон не смог подтвердить запись на это время 😔\n\n"
                        + describe(booking) + "\n\nВыберите, пожалуйста, другое время.")
                .replyMarkup(keyboard(List.of(webAppButton("Записаться снова"))))
                .build());
    }

    @Override
    public void notifyCancelledByClient(Booking booking) {
        if (!isEnabled() || adminChatId.isEmpty()) {
            return;
        }
        safeExecute(SendMessage.builder()
                .chatId(adminChatId)
                .text(adminCard("❌ Клиент отменил запись", booking))
                .build());
    }

    @Override
    public void sendReminder(Booking booking, int hoursBefore) {
        if (!isEnabled() || booking.getTelegramUserId() == null) {
            return;
        }
        String lead = hoursBefore >= 24 ? "Напоминаем: завтра у вас запись ⏰" : "Напоминаем: через пару часов у вас запись ⏰";
        StringBuilder text = new StringBuilder(lead).append("\n\n").append(describe(booking));
        appendAddress(text);
        safeExecute(SendMessage.builder()
                .chatId(booking.getTelegramUserId())
                .text(text.toString())
                .replyMarkup(keyboard(List.of(callbackButton("Отменить запись", CB_CANCEL + booking.getId()))))
                .build());
    }

    // ------------------------------------------------------------------ helpers

    private String describe(Booking booking) {
        LocalDateTime start = booking.getSlot().getStartTime();
        return "💇 " + booking.getService().getName() + "\n"
                + "👩 Мастер: " + booking.getSlot().getMaster().getName() + "\n"
                + "🕒 " + start.format(WHEN) + "\n"
                + "💰 " + String.format(Locale.US, "%,d", booking.getService().getPriceTenge()).replace(',', ' ') + " ₸";
    }

    private String statusLine(Booking booking) {
        return BookingStatus.PENDING.equals(booking.getStatus()) ? "⏳ Ожидает подтверждения" : "✅ Подтверждена";
    }

    private void appendAddress(StringBuilder text) {
        if (!salonAddress.isEmpty()) {
            text.append("\n📍 ").append(salonName).append(", ").append(salonAddress);
        }
    }

    private InlineKeyboardButton webAppButton(String text) {
        return InlineKeyboardButton.builder().text(text).webApp(new WebAppInfo(miniAppUrl)).build();
    }

    private static InlineKeyboardButton callbackButton(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    /** Каждый переданный список — отдельный ряд кнопок. */
    @SafeVarargs
    private static InlineKeyboardMarkup keyboard(List<InlineKeyboardButton>... rows) {
        return new InlineKeyboardMarkup(List.of(rows));
    }

    private void answerCallback(CallbackQuery callback, String text) {
        AnswerCallbackQuery.AnswerCallbackQueryBuilder answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callback.getId());
        if (text != null) {
            answer.text(text);
        }
        safeExecute(answer.build());
    }

    private void editMessage(Message message, String text) {
        if (message == null) {
            return;
        }
        safeExecute(EditMessageText.builder()
                .chatId(message.getChatId())
                .messageId(message.getMessageId())
                .text(text)
                .build());
    }

    private <T extends Serializable> void safeExecute(BotApiMethod<T> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            log.error("Telegram API call {} failed", method.getMethod(), e);
        }
    }
}
