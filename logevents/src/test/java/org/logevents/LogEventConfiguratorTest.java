package org.logevents;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.status.StatusEvent;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogEventConfiguratorTest {
    private static boolean configurator1Called = false;

    public static class Configurator1 implements LogEventConfigurator {
        @Override
        public void configure(LogEventFactory factory) {
            configurator1Called = true;
        }

        @Override
        public Properties loadConfigurationProperties() {
            return null;
        }

    }

    private static boolean configurator2Called = false;
    private Path serviceLoaderDir = Paths.get("target", "test-classes", "META-INF", "services");
    private Path serviceLoaderDir2 = Paths.get("target", "classes", "META-INF", "services");

    public static class Configurator2 implements LogEventConfigurator {
        @Override
        public void configure(LogEventFactory factory) {
            configurator2Called = true;
        }

        @Override
        public Properties loadConfigurationProperties() {
            return null;
        }
    }

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule();


    @Test
    public void shouldRegisterConfiguratorWithServiceLoader() throws IOException {
        Files.createDirectories(serviceLoaderDir);
        Files.write(serviceLoaderDir.resolve(LogEventConfigurator.class.getName()),
                Configurator1.class.getName().getBytes());

        Files.createDirectories(serviceLoaderDir2);
        Files.write(serviceLoaderDir2.resolve(LogEventConfigurator.class.getName()),
                Configurator2.class.getName().getBytes());

        configurator1Called = false;
        configurator2Called = false;
        LogEventFactory.getInstance().configure();
        assertTrue(configurator1Called);
        assertTrue(configurator2Called);
    }

    @Test
    public void shouldResetToDefault() throws IOException {
        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.ERROR);

        Files.deleteIfExists(serviceLoaderDir.resolve(LogEventConfigurator.class.getName()));
        Files.deleteIfExists(serviceLoaderDir2.resolve(LogEventConfigurator.class.getName()));

        LogEventFactory.getInstance().setRootLevel(Level.DEBUG);
        LogEventFactory.getInstance().configure();

        assertEquals(Level.WARN, LogEventFactory.getInstance().getRootLogger().getLevelThreshold());
    }

    @After
    public void deleteFiles() throws IOException {
        Files.deleteIfExists(serviceLoaderDir.resolve(LogEventConfigurator.class.getName()));
        Files.deleteIfExists(serviceLoaderDir2.resolve(LogEventConfigurator.class.getName()));
    }

}
