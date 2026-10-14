/**
 * Subclass of {@link User} representing a student.
 * <p>
 * CSV schema (students.csv): <b>StudentID, Name, Major, Year, Email, Password</b><br>
 * The <em>Password</em> column is optional; blank/null defaults to {@code "password"} via {@link User#setPassword(String)}.
 * </p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>{@code major} is normalized to upper-case and never null (null → {@code ""}).</li>
 *   <li>{@code year} is expected in the range {@code 1..4} (validated by callers/CSV importers).</li>
 *   <li>Compatibility aliases {@code getStudentId}/{@code setStudentId} wrap {@link User#getUserId()}.</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public class Student extends User {
    /** Student's declared major (e.g., {@code "CSC"}). Never null; {@code ""} if unknown. */
    private String major;

    /** Year of study (expected 1..4). */
    private int year;

    /**
     * Full constructor mapping CSV fields to the domain object.
     *
     * @param studentId unique student login identifier (becomes {@code userId})
     * @param name      student name
     * @param major     major (null becomes {@code ""}, stored upper-case)
     * @param year      year of study (expected 1..4)
     * @param email     contact email
     * @param password  plaintext password; blank/null becomes {@code "password"}
     */
    public Student(String studentId, String name, String major, int year, String email, String password) {
        super(studentId, name, email, password, "STUDENT");
        this.major = (major == null) ? "" : major.trim().toUpperCase();
        this.year = year;
    }

    /**
     * Convenience constructor when the Password column is absent.
     * Uses {@code "password"} as the default initial password.
     *
     * @param studentId unique student login identifier
     * @param name      student name
     * @param major     major (null → {@code ""}, stored upper-case)
     * @param year      year of study (expected 1..4)
     * @param email     contact email
     */
    public Student(String studentId, String name, String major, int year, String email) {
        this(studentId, name, major, year, email, "password");
    }

    /* ---------- Compatibility helpers for existing code/CSV utils ---------- */

    /**
     * Alias for {@link User#getUserId()} kept for backwards compatibility with older code.
     * @return student ID (same value as {@code getUserId()})
     */
    public String getStudentId() { return getUserId(); }

    /**
     * Alias for {@link User#setUserId(String)} kept for backwards compatibility.
     * @param id new student ID
     */
    public void setStudentId(String id) { setUserId(id); }

    /* --------------------------- Getters/Setters --------------------------- */

    /**
     * Returns the major. Guaranteed non-null (may be {@code ""}); stored upper-case.
     */
    public String getMajor() { return major; }

    /**
     * Sets the major, normalizing null to {@code ""} and upper-casing the value.
     * @param major major string to set
     */
    public void setMajor(String major) { this.major = (major == null) ? "" : major.trim().toUpperCase(); }

    /**
     * Returns the year of study (caller ensures range validity).
     */
    public int getYear() { return year; }

    /**
     * Sets the year of study (expected 1..4; validated by caller/CSV import).
     * @param year year to set
     */
    public void setYear(int year) { this.year = year; }

    /**
     * Compact debug representation: {@code studentId | name | MAJOR | Y# | email}.
     */
    @Override
    public String toString() {
        return String.format("%s | %s | %s | Y%d | %s",
                getStudentId(), getName(), major, year, getEmail());
    }
}
