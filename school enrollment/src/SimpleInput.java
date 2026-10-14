import java.util.Scanner;

/**
 * Minimal, reusable console input helper for the CLI.
 * <p>
 * Centralizes common input patterns (non-empty strings, defaults,
 * bounded integers, and yes/no prompts) with simple validation loops.
 * All returned strings are {@code trim()}med unless otherwise noted.
 * </p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Keeps a single {@link Scanner} on {@code System.in}. It is <b>not</b> closed
 *       intentionally to avoid closing the underlying {@code System.in} stream.</li>
 *   <li>All prompts are printed synchronously and block until valid input is read.</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public class SimpleInput {
    /** Shared scanner over {@code System.in}. Never closed to preserve the stream. */
    private final Scanner sc = new Scanner(System.in);

    /**
     * Reads a raw line (may be blank) after showing a prompt.
     *
     * @param prompt text to display before reading
     * @return the line as entered (including possible blanks), or {@code ""} if EOF produced {@code null}
     */
    public String readLine(String prompt) {
        System.out.print(prompt);
        String s = sc.nextLine();
        return (s == null) ? "" : s;
    }

    /**
     * Reads a non-empty, trimmed line. Re-prompts until a non-empty value is provided.
     *
     * @param prompt text to display before reading
     * @return trimmed, non-empty string
     */
    public String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine();
            if (s != null && !s.trim().isEmpty()) return s.trim();
            System.out.println("Input cannot be empty.");
        }
    }

    /**
     * Reads a line and returns a default if the user enters nothing (blank after trimming).
     *
     * @param prompt text to display before reading
     * @param def    value to return when user input is blank or {@code null}
     * @return trimmed input if non-blank; otherwise {@code def}
     */
    public String readDefault(String prompt, String def) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null) return def;
        s = s.trim();
        return s.isEmpty() ? def : s;
    }

    /**
     * Reads an integer, re-prompting until a valid {@link Integer} can be parsed.
     * Whitespace is ignored around the number.
     *
     * @param prompt text to display before reading
     * @return parsed integer
     */
    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine();
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    /**
     * Reads an integer restricted to the inclusive range [{@code min}, {@code max}].
     * Re-prompts until a valid, in-range value is provided.
     *
     * @param prompt text to display before reading
     * @param min    inclusive minimum acceptable value
     * @param max    inclusive maximum acceptable value
     * @return validated integer within the given bounds
     */
    public int readIntRange(String prompt, int min, int max) {
        while (true) {
            int v = readInt(prompt);
            if (v < min || v > max) {
                System.out.printf("Please enter a number between %d and %d.%n", min, max);
            } else return v;
        }
    }

    /**
     * Reads a yes/no response, accepting "y" / "yes" or "n" / "no" (case-insensitive).
     * Re-prompts until a valid response is entered.
     *
     * @param prompt text to display before reading (the method appends {@code " [y/n]: "})
     * @return {@code true} for yes, {@code false} for no
     */
    public boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt + " [y/n]: ");
            String s = sc.nextLine();
            if (s == null) continue;
            s = s.trim().toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("Please answer y or n.");
        }
    }
}
