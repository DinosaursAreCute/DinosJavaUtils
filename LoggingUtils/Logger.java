import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public Logger(String loggerName) {
        this.loggerName = loggerName;
        initializeLogFile();
    }

    // --- Public API ---

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

    public int getLogLevel() {
        return logLevel;
    }

    public String getLogLevelString() {
        if (logLevel == -1) return "OFF";
        if (logLevel < 0 || logLevel >= LOG_LEVELS.length) {
            error("Invalid log level: " + logLevel);
            return "OFF";
        }
        return LOG_LEVELS[logLevel];
    }

    public void setMaxFileSize(long maxBytes) {
        this.maxFileSize = maxBytes;
    }

    public void setFileLoggingEnabled(boolean enabled) {
        this.fileLoggingEnabled = enabled;
    }

    public void setConsoleLoggingEnabled(boolean enabled) {
        this.consoleLoggingEnabled = enabled;
    }

    public void setAppendToFile(boolean append) {
        this.appendToFile = append;
        reinitializeLogFile();
    }

    public boolean isAppendToFile() {
        return appendToFile;
    }

    public void close() {
        synchronized (this) {
            try {
                if (logWriter != null) logWriter.close();
            } catch (IOException e) {
                System.err.println(RED + "Logger: Error closing log file: " + e.getMessage() + RESET);
            }
        }
    }

    public void debug(String message) {
        log(0, message, PURPLE);
    }

    public void info(String message) {
        log(1, message, CYAN);
    }

    public void warning(String message) {
        log(2, message, YELLOW);
    }

    public void error(String message) {
        log(3, message, RED);
    }

    // --- Internal Methods ---

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

    private String formatLogLine(String timestamp, String level, String loggerName, String methodName, String message) {
        return String.format("%s %-9s %-32s %s",
                timestamp,
                "[" + level + "]",
                "[" + loggerName + "." + methodName + "]",
                message);
    }

    private void printToConsole(String logLine, String color) {
        System.out.println(color + logLine + RESET);
    }

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

    private void rotateLogFileIfNeeded() throws IOException {
        if (logFilePath != null && Files.exists(logFilePath) && Files.size(logFilePath) >= maxFileSize) {
            rotateLogFile();
        }
    }

    private void rotateLogFile() throws IOException {
        logWriter.close();
        logFileIndex++;
        String baseName = logFilePath.getFileName().toString().replaceAll("\\.txt$", "");
        Path newPath = Paths.get(baseName + "_part" + logFileIndex + ".txt");
        logFilePath = newPath;
        logWriter = new BufferedWriter(new FileWriter(logFilePath.toFile(), appendToFile));
        System.out.println(YELLOW + "Logger: Log file rotated to " + logFilePath + RESET);
    }

    private void handleFileWriteError(IOException e) {
        fileWriteError = true;
        fileLoggingEnabled = false;
        System.err.println(RED + "Logger: Error writing to log file. File logging disabled. Error: " + e.getMessage() + RESET);
    }

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
