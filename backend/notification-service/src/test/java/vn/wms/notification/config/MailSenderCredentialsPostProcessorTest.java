package vn.wms.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailSenderCredentialsPostProcessorTest {

    private static JavaMailSenderImpl sender(String username, String password, int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.example.com");
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        return sender;
    }

    @Test
    void blankCredentialsAreDroppedSoNoAuthIsAttempted() {
        JavaMailSenderImpl sender = sender("", "", 1025);

        MailSenderCredentialsPostProcessor.normalize(sender);

        assertThat(sender.getUsername()).isNull();
        assertThat(sender.getPassword()).isNull();
        assertThat(sender.getJavaMailProperties()).doesNotContainKey("mail.smtp.auth");
    }

    @Test
    void credentialsOnSubmissionPortEnableAuthAndStartTls() {
        JavaMailSenderImpl sender = sender("kho@example.com", "app-password", 587);

        MailSenderCredentialsPostProcessor.normalize(sender);

        assertThat(sender.getUsername()).isEqualTo("kho@example.com");
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true");
    }

    @Test
    void credentialsOnSmtpsPortEnableAuthAndSsl() {
        JavaMailSenderImpl sender = sender("kho@example.com", "app-password", 465);

        MailSenderCredentialsPostProcessor.normalize(sender);

        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.ssl.enable", "true")
                .doesNotContainKey("mail.smtp.starttls.enable");
    }

    @Test
    void explicitMailPropertiesAreNeverOverridden() {
        JavaMailSenderImpl sender = sender("kho@example.com", "app-password", 587);
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "false");

        MailSenderCredentialsPostProcessor.normalize(sender);

        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.starttls.enable", "false");
    }
}
