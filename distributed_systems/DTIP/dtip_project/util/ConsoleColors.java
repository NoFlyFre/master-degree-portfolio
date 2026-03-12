package util;

/**
 * Utility class providing ANSI escape codes for colored console output.
 * Improves readability of logs during development and demonstrations.
 */
public class ConsoleColors {
    /** Resets color to default. */
    public static final String RESET = "\033[0m";

    // Regular Colors
    /** Black color code. */
    public static final String BLACK = "\033[0;30m";
    /** Red color code. */
    public static final String RED = "\033[0;31m";
    /** Green color code. */
    public static final String GREEN = "\033[0;32m";
    /** Yellow color code. */
    public static final String YELLOW = "\033[0;33m";
    /** Blue color code. */
    public static final String BLUE = "\033[0;34m";
    /** Purple color code. */
    public static final String PURPLE = "\033[0;35m";
    /** Cyan color code. */
    public static final String CYAN = "\033[0;36m";
    /** White color code. */
    public static final String WHITE = "\033[0;37m";

    // Bold
    /** Bold Red color code. */
    public static final String RED_BOLD = "\033[1;31m";
    /** Bold Green color code. */
    public static final String GREEN_BOLD = "\033[1;32m";
    /** Bold Yellow color code. */
    public static final String YELLOW_BOLD = "\033[1;33m";
    /** Bold Blue color code. */
    public static final String BLUE_BOLD = "\033[1;34m";
    /** Bold Cyan color code. */
    public static final String CYAN_BOLD = "\033[1;36m";
    /** Bold Purple color code. */
    public static final String PURPLE_BOLD = "\033[1;35m";

    // Backgrounds
    /** Green background code. */
    public static final String GREEN_BACKGROUND = "\033[42m";
    /** Red background code. */
    public static final String RED_BACKGROUND = "\033[41m";

    /** Private constructor to prevent instantiation. */
    private ConsoleColors() {}

    /**
     * Prints a success message (green tick) to stdout.
     * @param msg The message to print.
     */
    public static void printSuccess(String msg) {
        System.out.println(GREEN_BOLD + "✅ " + msg + RESET);
    }

    /**
     * Prints an error message (red cross) to stdout.
     * @param msg The message to print.
     */
    public static void printError(String msg) {
        System.out.println(RED_BOLD + "❌ " + msg + RESET);
    }

    /**
     * Prints an info message (blue info icon) to stdout.
     * @param msg The message to print.
     */
    public static void printInfo(String msg) {
        System.out.println(BLUE + "ℹ️ " + msg + RESET);
    }
}