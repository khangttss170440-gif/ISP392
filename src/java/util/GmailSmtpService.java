package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Sends account verification codes through Gmail SMTP using a Gmail App Password. */
public final class GmailSmtpService {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 465;
    private static final int TIMEOUT_MILLIS = 15000;

    private GmailSmtpService() { }

    public static void sendRegistrationCode(String recipient, String name,
            String code) throws IOException {
        sendCode(recipient, name, code,
                "Mã xác thực tài khoản MENSFORM",
                "Mã xác thực đăng ký tài khoản MENSFORM của bạn là: ",
                "Nếu bạn không thực hiện đăng ký, hãy bỏ qua email này.");
    }

    public static void sendPasswordResetCode(String recipient, String name,
            String code) throws IOException {
        sendCode(recipient, name, code,
                "Mã đặt lại mật khẩu MENSFORM",
                "Mã xác nhận đặt lại mật khẩu MENSFORM của bạn là: ",
                "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
    }

    public static void sendPasswordChangeCode(String recipient, String name,
            String code) throws IOException {
        sendCode(recipient, name, code,
                "Xác nhận đổi mật khẩu MENSFORM",
                "Mã xác nhận đổi mật khẩu MENSFORM của bạn là: ",
                "Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này.");
    }

    private static void sendCode(String recipient, String name, String code,
            String subjectText, String codeText, String warningText)
            throws IOException {
        if (recipient == null || !recipient.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IOException("Invalid recipient email address.");
        }
        String username = requiredEnvironmentValue("GMAIL_SMTP_USER");
        String appPassword = requiredEnvironmentValue("GMAIL_APP_PASSWORD")
                .replaceAll("\\s+", "");

        try (Socket raw = SSLSocketFactory.getDefault()
                    .createSocket(SMTP_HOST, SMTP_PORT)) {
            raw.setSoTimeout(TIMEOUT_MILLIS);
            SSLSocket socket = (SSLSocket) raw;
            socket.startHandshake();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));

            expect(readResponse(reader), 220, "SMTP greeting");
            command(writer, reader, "EHLO mensform.local", 250);
            command(writer, reader, "AUTH LOGIN", 334);
            command(writer, reader, Base64.getEncoder().encodeToString(
                    username.getBytes(StandardCharsets.UTF_8)), 334);
            command(writer, reader, Base64.getEncoder().encodeToString(
                    appPassword.getBytes(StandardCharsets.UTF_8)), 235);
            command(writer, reader, "MAIL FROM:<" + username + ">", 250);
            command(writer, reader, "RCPT TO:<" + recipient + ">", 250, 251);
            command(writer, reader, "DATA", 354);

            String greetingName = safeHeader(name);
            String subject = Base64.getEncoder().encodeToString(
                    subjectText.getBytes(StandardCharsets.UTF_8));
            String body = "Xin chào " + greetingName + ",\n\n"
                    + codeText + code + "\n\nMã có hiệu lực trong 10 phút. "
                    + warningText;
            String encodedBody = Base64.getMimeEncoder(76,
                    "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(body.getBytes(StandardCharsets.UTF_8));

            writer.write("From: MENSFORM <" + username + ">\r\n");
            writer.write("To: <" + safeHeader(recipient) + ">\r\n");
            writer.write("Subject: =?UTF-8?B?" + subject + "?=\r\n");
            writer.write("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(ZonedDateTime.now(java.time.ZoneOffset.UTC)) + "\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("Content-Transfer-Encoding: base64\r\n\r\n");
            writer.write(encodedBody.replace("\r\n", "\r\n"));
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(readResponse(reader), 250, "Message delivery");
            writer.write("QUIT\r\n");
            writer.flush();
            readResponse(reader);
        }
    }

    private static void command(BufferedWriter writer, BufferedReader reader,
            String text, int... expectedCodes) throws IOException {
        writer.write(text);
        writer.write("\r\n");
        writer.flush();
        expect(readResponse(reader), expectedCodes, text.startsWith("AUTH")
                ? "SMTP authentication" : "SMTP command");
    }

    private static int readResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.length() < 3) {
            throw new IOException("Gmail SMTP closed the connection unexpectedly.");
        }
        int code;
        try {
            code = Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            throw new IOException("Gmail SMTP returned an invalid response.");
        }
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) throw new IOException("Incomplete Gmail SMTP response.");
        }
        return code;
    }

    private static void expect(int actualCode, int expectedCode, String stage)
            throws IOException {
        expect(actualCode, new int[]{expectedCode}, stage);
    }

    private static void expect(int actualCode, int[] expectedCodes, String stage)
            throws IOException {
        for (int expected : expectedCodes) {
            if (actualCode == expected) return;
        }
        throw new IOException("Gmail SMTP rejected " + stage
                + " with response code " + actualCode + ".");
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " is not configured.");
        }
        return value.trim();
    }

    private static String safeHeader(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }
}
