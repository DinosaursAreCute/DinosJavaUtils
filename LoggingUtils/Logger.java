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

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] LOG_LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR"};

    private final String loggerName;
    private int logLevel = 0; // 0=DEBUG, 1=INFO, 2=WARNING, 3=ERROR, -1=OFF

    public Logger(String loggerName) {
        this.loggerName = loggerName;
    }

    public void setLogLevel(int logLevel) {
        if ((logLevel >= 0 && logLevel < LOG_LEVELS.length) || logLevel == -1) {
            if (logLevel == -1 && this.logLevel != -1) {
                // Optionally print message before turning off
                System.out.println(CYAN + String.format("%-19s %-9s %-32s %s",
                        "",
                        "[INFO]",
                        "[" + loggerName + "]",
                        "Logging is now turned OFF.") + RESET);
            } else if (logLevel != -1) {
                info("Log level set to: " + LOG_LEVELS[logLevel]);
            }
            this.logLevel = logLevel;
        } else if (this.logLevel != -1) {
            error("Invalid log level: " + logLevel);
        }
    }

    public int getLogLevel() {
        return logLevel;
    }

    // Added method to get calling method name
    private String getCallingMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stackTrace) {
            // Skip this logger class and java.lang.Thread
            if (!ste.getClassName().equals(Logger.class.getName()) && !ste.getClassName().equals(Thread.class.getName())) {
                return ste.getMethodName();
            }
        }
        return "UnknownMethod";
    }

    // Modified to append method name to loggerName in output
    private void log(int level, String message, String color) {
        if (this.logLevel == -1) return;
        if (level >= this.logLevel && level < LOG_LEVELS.length && message != null) {
            String timestamp = dtf.format(LocalDateTime.now());
            String methodName = getCallingMethodName(); // <-- added
            String logLine = String.format(
                    "%s %-9s %-32s %s",
                    timestamp,
                    String.format("[%s]", LOG_LEVELS[level]),
                    String.format("[%s.%s]", loggerName, methodName), // <-- changed
                    message
            );
            System.out.println(color + logLine + RESET);
        }
    }

    public void error(String message) {
        log(3, message, RED);
    }
    public void warning(String message) {
        log(2, message, YELLOW);
    }
    public void info(String message) {
        log(1, message, CYAN);
    }
    public void debug(String message) {
        log(0, message, PURPLE);
    }

    // Example: Logger logger = new Logger("MyComponent");
    // logger.info("Hello from MyComponent!");
}
