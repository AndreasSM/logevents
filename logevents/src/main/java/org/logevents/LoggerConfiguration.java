package org.logevents;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

/**
 * The configuration information of a Logger. Used for
 * logic that reports the current configuration.
 *
 * @author Johannes Brodwall
 *
 */
public interface LoggerConfiguration extends Logger, LocationAwareLogger {

    Level getLevelThreshold();

    /**
     * Calls toString on the observer for this Logger.
     */
    String getObserver();

    void replaceObserver(LogEventObserver observer);
}
