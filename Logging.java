import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Logging {

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 0 = Debug 1 = INFO, 2 = WARNING, 3 = ERROR, -1 = OFF

    static String[] logLevels = {"DEBUG", "INFO", "WARNING", "ERROR"};
    static int logLevel = 0; /// Default log level

    // Helper to get calling class and method for automatic logging
    private static String getCallingClassAndMethod() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stackTrace) {
            if (!ste.getClassName().equals(Logging.class.getName())
                    && !ste.getClassName().equals(Thread.class.getName())) {
                return ste.getClassName() + "." + ste.getMethodName();
            }
        }
        return "UnknownClass#UnknownMethod";
    }

    // The core logging logic is now inside these private log functions
    public static void log(int level, String ofClass, String message, String color) {
        if (Logging.logLevel == -1) return;
        if (level >= Logging.logLevel && level < logLevels.length && level >= 0 && ofClass != null && message != null) {
            String timestamp = dtf.format(LocalDateTime.now());
            // To align columns, use fixed width for each field and remove spaces inside square brackets
            // Timestamp: 19 chars | Level: 9 chars | Class: 28 chars, all left aligned
            String logLine = String.format(
                    "%s %-9s %-32s %s",
                    timestamp,
                    String.format("[%s]", logLevels[level]),
                    String.format("[%s]", ofClass),
                    message
            );
            System.out.println(color + logLine + RESET);
        }
    }

    // Only the public logX methods should be used internally and externally
    public static void logError(String ofClass, String message) {
        log(3, ofClass, message, RED);
    }
    public static void logWarning(String ofClass, String message) {
        log(2, ofClass, message, YELLOW);
    }
    public static void logInfo(String ofClass, String message) {
        log(1, ofClass, message, CYAN);
    }
    public static void logDebug(String ofClass, String message) {
        log(0, ofClass, message, RESET);
    }

    // Logging methods with automatic class/method detection
    public static void logError(String message) {
        logError(getCallingClassAndMethod(), message);
    }
    public static void logWarning(String message) {
        logWarning(getCallingClassAndMethod(), message);
    }
    public static void logInfo(String message) {
        logInfo(getCallingClassAndMethod(), message);
    }
    public static void logDebug(String message) {
        logDebug(getCallingClassAndMethod(), message);
    }

    public static void setLogLevel(int logLevel) {
        if ((logLevel >= 0 && logLevel < logLevels.length) || logLevel == -1) {
            if (logLevel == -1 && Logging.logLevel != -1) {
                // Logging is being turned off, optionally print message before turning off
                // This manually aligns with the other logs for aesthetics
                System.out.println(CYAN + String.format("%-19s %-9s %-28s %s",
                        "",
                        "[INFO]",
                        "[Logging.setLogLevel]",
                        "Logging is now turned OFF.") + RESET);
            } else if (logLevel != -1) {
                logInfo("Logging.setLogLevel", "Log level set to: " + logLevels[logLevel]);
            }
            Logging.logLevel = logLevel;
        } else if (Logging.logLevel != -1) {
            logError("Logging.setLogLevel", "Invalid log level: " + logLevel);
        }
    }
    public static void runTest(){testLog();}
    // Helper method to print log messages with the default color
    public static void testLog() {
        logDebug("Logging.testLogging", "This is a debug message.");
        logInfo("Logging.testLogging", "This is an info message.");
        logWarning("Logging.testLogging", "This is a warning message.");
        logError("Logging.testLogging", "This is an error message.");

        // Test automatic log methods
        logDebug("This is an auto-debug message.");
        logInfo("This is an auto-info message.");
        logWarning("This is an auto-warning message.");
        logError("This is an auto-error message.");

        // Test setting log level
        setLogLevel(-1); // Turn off logging
        logDebug("Logging.testLogging","This debug message should not appear.");
        setLogLevel(0); // Set log level to DEBUG
        logDebug("Logging.testLogging","This debug message should appear.");
        setLogLevel(1); // Set log level to INFO
        logDebug("Logging.testLogging","This debug message should not appear.");
        logInfo("Logging.testLogging","This info message should appear.");
        setLogLevel(2); // Set log level to WARNING
        logInfo("Logging.testLogging","This info message should not appear.");
        logWarning("Logging.testLogging","This warning message should appear.");
        setLogLevel(3); // Set log level to ERROR
        logWarning("Logging.testLogging","This warning message should not appear.");
        logError("Logging.testLogging","This error message should appear.");
    }
}
