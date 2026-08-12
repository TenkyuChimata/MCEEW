package jp.wolfx.mceew.velocity;

import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.slf4j.Logger;

/** Owns an isolated JUL logger that forwards core runtime records to Velocity's SLF4J logger. */
final class VelocityJulLogger implements AutoCloseable {
    private final java.util.logging.Logger julLogger;
    private final Handler handler;
    private boolean closed;

    VelocityJulLogger(Logger platformLogger) {
        handler = new Slf4jHandler(Objects.requireNonNull(platformLogger, "platformLogger"));
        handler.setLevel(Level.ALL);
        julLogger = java.util.logging.Logger.getAnonymousLogger();
        julLogger.setUseParentHandlers(false);
        julLogger.setLevel(Level.ALL);
        julLogger.addHandler(handler);
    }

    java.util.logging.Logger logger() {
        return julLogger;
    }

    int handlerCount() {
        return julLogger.getHandlers().length;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        julLogger.removeHandler(handler);
        handler.close();
    }

    private static final class Slf4jHandler extends Handler {
        private final Logger logger;

        private Slf4jHandler(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void publish(LogRecord record) {
            if (record == null || !isLoggable(record)) {
                return;
            }
            String message = record.getMessage();
            Throwable error = record.getThrown();
            int level = record.getLevel().intValue();
            if (level >= Level.SEVERE.intValue()) {
                logError(message, error);
            } else if (level >= Level.WARNING.intValue()) {
                logWarning(message, error);
            } else if (level >= Level.INFO.intValue()) {
                logInfo(message, error);
            } else {
                logDebug(message, error);
            }
        }

        private void logError(String message, Throwable error) {
            if (error == null) {
                logger.error(message);
            } else {
                logger.error(message, error);
            }
        }

        private void logWarning(String message, Throwable error) {
            if (error == null) {
                logger.warn(message);
            } else {
                logger.warn(message, error);
            }
        }

        private void logInfo(String message, Throwable error) {
            if (error == null) {
                logger.info(message);
            } else {
                logger.info(message, error);
            }
        }

        private void logDebug(String message, Throwable error) {
            if (error == null) {
                logger.debug(message);
            } else {
                logger.debug(message, error);
            }
        }

        @Override
        public void flush() {
            // SLF4J owns its output lifecycle.
        }

        @Override
        public void close() {
            // The injected Velocity logger is platform-owned.
        }
    }
}
