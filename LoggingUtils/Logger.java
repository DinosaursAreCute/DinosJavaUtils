import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger is a flexible, thread-safe logging utility with support for
 * console and file logging, log rotation, and runtime configuration.
 * <p>
 * Features:
 * - Log levels: DEBUG, INFO, WARNING, ERROR, OFF
 * - Console and file output
 * - File appending or rolling log file with timestamp
 * - Automatic log rotation based on file size (default 1MB)
 * - Thread safety for file operations
 * - ANSI color-coded console output
 */
public class Logger {
    // ANSI color constants
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";

    private static final DateTimeFormatter LOG_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DTF = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String[] LOG_LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR"};
    private static final long DEFAULT_MAX_FILE_SIZE = 1024 * 1024; // 1MB

    private final String loggerName;
    private int logLevel = 0; // 0=DEBUG, 1=INFO, 2=WARNING, 3=ERROR, -1=OFF

    private boolean consoleLoggingEnabled = true;
    private boolean fileLoggingEnabled = true;
    private boolean appendToFile = true;
    private long maxFileSize = DEFAULT_MAX_FILE_SIZE;

    private BufferedWriter logWriter;
    private Path logFilePath;
    private boolean fileWriteError = false;
    private int logFileIndex = 0;

    /**
     * Constructs a new Logger instance with the given name.
     * The logger will write to console and to a file (log.txt if appending, or timestamped file if not).
     * @param loggerName a string identifying the logger (shown in output)
     */
    public Logger(String loggerName) {
        this.loggerName = loggerName;
        initializeLogFile();
    }

    // --- Public API ---

    /**
     * Sets the log level. Allowed: 0=DEBUG, 1=INFO, 2=WARNING, 3=ERROR, -1=OFF.
     * @param logLevel the log level
     */
    public void setLogLevel(int logLevel) {
        if ((logLevel >= 0 && logLevel < LOG_LEVELS.length) || logLevel == -1) {
            handleLogLevelChange(logLevel);
            this.logLevel = logLevel;
        } else if (this.logLevel != -1) {
            error("Invalid log level: " + logLevel);
            warning("Automatic resolution: Set log level to -1");
            this.logLevel = -1;
        }
    }

    /**
     * Gets the current log level as integer.
     * @return log level int
     */
    public int getLogLevel() {
        return logLevel;
    }

    /**
     * Gets the current log level as a string.
     * @return log level string ("DEBUG", "INFO", "WARNING", "ERROR", "OFF")
     */
    public String getLogLevelString() {
        if (logLevel == -1) return "OFF";
        if (logLevel < 0 || logLevel >= LOG_LEVELS.length) {
            error("Invalid log level: " + logLevel);
            return "OFF";
        }
        return LOG_LEVELS[logLevel];
    }

    /**
     * Sets the maximum size of the log file in bytes before rotation occurs.
     * @param maxBytes the maximum file size in bytes
     */
    public void setMaxFileSize(long maxBytes) {
        this.maxFileSize = maxBytes;
    }

    /**
     * Enables or disables file logging at runtime.
     * @param enabled true to enable, false to disable
     */
    public void setFileLoggingEnabled(boolean enabled) {
        this.fileLoggingEnabled = enabled;
    }

    /**
     * Enables or disables console logging at runtime.
     * @param enabled true to enable, false to disable
     */
    public void setConsoleLoggingEnabled(boolean enabled) {
        this.consoleLoggingEnabled = enabled;
    }

    /**
     * Sets whether to append to file (log.txt) or create a new file per run (timestamped).
     * Re-initializes the log file.
     * @param append true to append to log.txt, false for a new file per run
     */
    public void setAppendToFile(boolean append) {
        this.appendToFile = append;
        reinitializeLogFile();
    }

    /**
     * Returns whether file appending is enabled.
     * @return true if in append mode, false if new file per run
     */
    public boolean isAppendToFile() {
        return appendToFile;
    }

    /**
     * Closes the logger's file handle. Should be called at app shutdown.
     */
    public void close() {
        synchronized (this) {
            try {
                if (logWriter != null) logWriter.close();
            } catch (IOException e) {
                System.err.println(RED + "Logger: Error closing log file: " + e.getMessage() + RESET);
            }
        }
    }

    /**
     * Logs a DEBUG message.
     * @param message message to log
     */
    public void debug(String message) {
        log(0, message, PURPLE);
    }

    /**
     * Logs an INFO message.
     * @param message message to log
     */
    public void info(String message) {
        log(1, message, CYAN);
    }

    /**
     * Logs a WARNING message.
     * @param message message to log
     */
    public void warning(String message) {
        log(2, message, YELLOW);
    }

    /**
     * Logs an ERROR message.
     * @param message message to log
     */
    public void error(String message) {
        log(3, message, RED);
    }

    // --- Internal Methods ---

    /**
     * Logs a message to the appropriate outputs if enabled and level is met.
     * Applies thread safety for file writes.
     */
    private void log(int level, String message, String color) {
        if (logLevel == -1) return;
        if (level < logLevel || level >= LOG_LEVELS.length || message == null) return;

        String timestamp = LOG_DTF.format(LocalDateTime.now());
        String methodName = getCallingMethodName();
        String logLine = formatLogLine(timestamp, LOG_LEVELS[level], loggerName, methodName, message);

        if (consoleLoggingEnabled) {
            printToConsole(logLine, color);
        }
        if (fileLoggingEnabled && !fileWriteError) {
            writeToFile(logLine);
        }
    }

    /**
     * Gets the name of the calling method for log context.
     * @return method name as a string
     */
    private String getCallingMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stackTrace) {
            if (!ste.getClassName().equals(Logger.class.getName()) &&
                    !ste.getClassName().equals(Thread.class.getName())) {
                return ste.getMethodName();
            }
        }
        return "UnknownMethod";
    }

    /**
     * Formats a log line for output.
     */
    private String formatLogLine(String timestamp, String level, String loggerName, String methodName, String message) {
        return String.format("%s %-9s %-32s %s",
                timestamp,
                "[" + level + "]",
                "[" + loggerName + "." + methodName + "]",
                message);
    }

    /**
     * Prints a log line to the console with color.
     */
    private void printToConsole(String logLine, String color) {
        System.out.println(color + logLine + RESET);
    }

    /**
     * Writes a log line to the log file, performing rotation if needed.
     * Synchronized for thread safety.
     */
    private void writeToFile(String logLine) {
        synchronized (this) {
            if (fileWriteError || logWriter == null) return;
            try {
                rotateLogFileIfNeeded();
                logWriter.write(logLine);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                handleFileWriteError(e);
            }
        }
    }

    /**
     * Checks the log file size and rotates to a new file if over the limit.
     */
    private void rotateLogFileIfNeeded() throws IOException {
        if (logFilePath != null && Files.exists(logFilePath) && Files.size(logFilePath) >= maxFileSize) {
            rotateLogFile();
        }
    }

    /**
     * Rotates the log file by closing the current one and creating a new file
     * with an incremented index.
     */
    private void rotateLogFile() throws IOException {
        logWriter.close();
        logFileIndex++;
        String baseName = logFilePath.getFileName().toString().replaceAll("\\.txt$", "");
        Path newPath = Paths.get(baseName + "_part" + logFileIndex + ".txt");
        logFilePath = newPath;
        logWriter = new BufferedWriter(new FileWriter(logFilePath.toFile(), appendToFile));
        System.out.println(YELLOW + "Logger: Log file rotated to " + logFilePath + RESET);
    }

    /**
     * Handles file write errors by disabling file logging and warning the user.
     */
    private void handleFileWriteError(IOException e) {
        fileWriteError = true;
        fileLoggingEnabled = false;
        System.err.println(RED + "Logger: Error writing to log file. File logging disabled. Error: " + e.getMessage() + RESET);
    }

    /**
     * Prints an info message when log level is changed.
     */
    private void handleLogLevelChange(int newLogLevel) {
        if (newLogLevel == -1 && this.logLevel != -1) {
            printToConsole(
                    String.format("%-19s %-9s %-32s %s",
                            "",
                            "[INFO]",
                            "[" + loggerName + "]",
                            "Logging is now turned OFF."
                    ), CYAN);
        } else if (newLogLevel != -1) {
            info("Log level set to: " + LOG_LEVELS[newLogLevel]);
        }
    }

    /**
     * Initializes the log file. Uses "log.txt" in append mode, or a timestamped
     * file for new-per-run mode.
     */
    private void initializeLogFile() {
        synchronized (this) {
            try {
                if (appendToFile) {
                    logFilePath = Paths.get("log.txt"); // Always append to this file
                } else {
                    String baseName = "log_" + FILE_DTF.format(LocalDateTime.now());
                    logFilePath = Paths.get(baseName + ".txt"); // New file per run
                }
                logWriter = new BufferedWriter(new FileWriter(logFilePath.toFile(), appendToFile));
                logFileIndex = 0;
                fileWriteError = false;
            } catch (IOException e) {
                fileWriteError = true;
                fileLoggingEnabled = false;
                System.err.println(RED + "Logger: Failed to initialize log file. File logging disabled. Error: " + e.getMessage() + RESET);
            }
        }
    }

    /**
     * Re-initializes the log file, closing the previous writer if present.
     */
    private void reinitializeLogFile() {
        synchronized (this) {
            try {
                if (logWriter != null) {
                    logWriter.close();
                }
            } catch (IOException ignored) {}
            initializeLogFile();
        }
    }
}
