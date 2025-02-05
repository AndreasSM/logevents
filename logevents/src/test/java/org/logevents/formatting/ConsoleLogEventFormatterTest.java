package org.logevents.formatting;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventSampler;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleLogEventFormatterTest {

    protected ConsoleFormatting format = ConsoleFormatting.getInstance();
    private ConsoleLogEventFormatter formatter = new ConsoleLogEventFormatter();
    private String loggerName = "com.example.LoggerName";
    private Instant time = ZonedDateTime.of(2018, 8, 1, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant();

    @Test
    public void shouldLogMessage() {
        String message = formatter.apply(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(time)
                .withThread("main")
                .withLoggerName(loggerName)
                .withFormat("Hello {}").withArgs("there")
                .build());
        assertEquals("10:00:00.000 [main] [\033[34mINFO \033[m] [\033[1;mConsoleLogEventFormatterTest.shouldLogMessage(ConsoleLogEventFormatterTest.java:32)\033[m]: Hello \033[4;mthere\033[m\n",
                message);
    }

    @Test
    public void shouldColorLogLevel() {
        assertEquals(format.boldRed("ERROR"), formatter.colorizedLevel(Level.ERROR));
        assertEquals(format.red("WARN "), formatter.colorizedLevel(Level.WARN));
        assertEquals(format.blue("INFO "), formatter.colorizedLevel(Level.INFO));
        assertEquals("DEBUG", formatter.colorizedLevel(Level.DEBUG));
    }

    @Test
    public void shouldDisplayMdc() {
        Properties properties = new Properties();
        properties.put("observer.console.includedMdcKeys", "operation,user");
        Configuration configuration = new Configuration(properties, "observer.console");
        formatter.configure(configuration);
        configuration.checkForUnknownFields();

        String message = formatter.apply(new LogEventSampler()
                .withMdc("operation", "op13")
                .withMdc("user", "userOne")
                .withMdc("secret", "secret value")
                .build());

        assertContains("{operation=op13, user=userOne}", message);
        assertDoesNotContain("secret value", message);
    }

    @Test
    public void shouldIncludeAllMdcKeysByDefault() {
        formatter.configure(new Configuration(new Properties(), "observer.console"));
        String message = formatter.apply(new LogEventSampler()
                .withMdc("op", "read")
                .withMdc("uid", "userFive")
                .build());
        assertContains("{op=read, uid=userFive}", message);
    }

    private void assertContains(String substring, String fullString) {
        assertTrue("Should find <" + substring + "> in <" + fullString + ">",
                fullString.contains(substring));
    }

    private void assertDoesNotContain(String substring, String fullString) {
        assertFalse("Should NOT find <" + substring + "> in <" + fullString + ">",
                fullString.contains(substring));
    }
}
