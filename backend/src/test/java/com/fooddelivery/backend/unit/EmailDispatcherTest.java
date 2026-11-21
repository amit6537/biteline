package com.fooddelivery.backend.unit;

import com.fooddelivery.backend.entity.EmailStatus;
import com.fooddelivery.backend.entity.Notification;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.notification.EmailSender;
import com.fooddelivery.backend.repository.NotificationRepository;
import com.fooddelivery.backend.repository.UserRepository;
import com.fooddelivery.backend.scheduler.EmailDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CONCEPT: Mocks (Mockito)
 * ────────────────────────
 * EmailDispatcher needs a database and a mail server. In a UNIT test we
 * replace them with mocks — fake objects whose answers we script:
 *   when(repo.lockDueEmails(anyInt())).thenReturn(List.of(notification));
 *   doThrow(...).when(sender).send(...);   // "the mail server is down"
 * Then we check what the dispatcher did to the notification.
 */
class EmailDispatcherTest {

    private NotificationRepository notifications;
    private UserRepository users;
    private EmailSender sender;
    private EmailDispatcher dispatcher;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        sender = mock(EmailSender.class);
        dispatcher = new EmailDispatcher(notifications, users, sender);
        ReflectionTestUtils.setField(dispatcher, "batchSize", 20);
        ReflectionTestUtils.setField(dispatcher, "maxAttempts", 5);
        ReflectionTestUtils.setField(dispatcher, "baseBackoff", Duration.ofSeconds(30));

        when(users.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("amit@t.com").build()));
    }

    private Notification pending(int attemptsSoFar) {
        return Notification.builder()
                .id(UUID.randomUUID()).userId(userId).title("Order placed").message("We got it")
                .emailStatus(EmailStatus.PENDING).emailAttempts(attemptsSoFar)
                .emailNextAttemptAt(LocalDateTime.now())
                .build();
    }

    @Test
    void successfulSendMarksSent() {
        Notification n = pending(0);
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(n));

        dispatcher.dispatchDueEmails();

        verify(sender).send(eq("amit@t.com"), eq("Order placed"), contains("We got it"));
        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(n.getEmailSentAt()).isNotNull();
    }

    @Test
    void firstFailureSchedulesRetryIn30Seconds() {
        Notification n = pending(0);
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(n));
        doThrow(new RuntimeException("SMTP down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDueEmails();

        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.PENDING); // not lost
        assertThat(n.getEmailAttempts()).isEqualTo(1);
        assertThat(n.getEmailLastError()).isEqualTo("SMTP down");
        assertThat(Duration.between(LocalDateTime.now(), n.getEmailNextAttemptAt()).getSeconds())
                .isCloseTo(30L, within(2L));
    }

    @Test
    void backoffDoublesEachTime() {
        Notification n = pending(3); // this will be attempt #4 → 30s × 2³ = 240s
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(n));
        doThrow(new RuntimeException("SMTP down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDueEmails();

        assertThat(Duration.between(LocalDateTime.now(), n.getEmailNextAttemptAt()).getSeconds())
                .isCloseTo(240L, within(2L));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        Notification n = pending(4); // this is attempt #5 of 5
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(n));
        doThrow(new RuntimeException("SMTP down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDueEmails();

        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.FAILED);
    }

    @Test
    void userDeletedMeansSkippedNotRetriedForever() {
        Notification n = pending(0);
        n.setUserId(UUID.randomUUID()); // unknown user
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(n));

        dispatcher.dispatchDueEmails();

        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.SKIPPED);
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void oneBadEmailDoesNotStopTheOthers() {
        Notification bad = pending(0);
        Notification good = pending(0);
        good.setTitle("Second");
        when(notifications.lockDueEmails(anyInt())).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("boom")).when(sender).send(anyString(), eq("Order placed"), anyString());

        dispatcher.dispatchDueEmails();

        assertThat(bad.getEmailStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(good.getEmailStatus()).isEqualTo(EmailStatus.SENT);
    }
}
