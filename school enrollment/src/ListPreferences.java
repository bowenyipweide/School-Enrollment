/**
 * Per-user list view preferences (filter + sort) used by menus.
 * <p>
 * Semantics:
 * <ul>
 *   <li>{@link #filterColumn}: which column to filter on. {@link Column#NONE} means "no filter".</li>
 *   <li>{@link #filterKeyword}: the keyword applied to the {@code filterColumn}.
 *       When {@code filterColumn == NONE}, this value is ignored and
 *       will be cleared to {@code null} by {@link #setFilterColumn(Column)}.</li>
 *   <li>{@link #sortColumn}: which column to sort by. {@link Column#NONE} means "no sort".</li>
 * </ul>
 *
 * <p>Persistence is handled by {@code PreferenceService} (CSV-backed). Typical flow:
 * <ol>
 *   <li>Load with {@code prefs = PreferenceService.getInstance().get(user, viewId)}</li>
 *   <li>Mutate via setters and re-save with {@code put(user, viewId, prefs)}</li>
 *   <li>Apply using {@code PortalService.applyOppPrefs(base, prefs)}</li>
 * </ol>
 *
 * <p>Defaults: {@code filterColumn = NONE}, {@code filterKeyword = null}, {@code sortColumn = NONE}.</p>
 *
 * @since v1.3
 */
public class ListPreferences {

    /** Column to filter on; {@link Column#NONE} means "no filter". */
    private Column filterColumn = Column.NONE;

    /** Keyword for filtering; ignored when {@link #filterColumn} is {@link Column#NONE}. */
    private String filterKeyword = null;

    /** Column to sort by; {@link Column#NONE} means "no sort". Sorting is ascending by design. */
    private Column sortColumn = Column.NONE;

    /** @return current filter column (never {@code null}) */
    public Column getFilterColumn() { return filterColumn; }

    /**
     * Sets the filter column. {@code null} is normalized to {@link Column#NONE}.
     * If set to {@link Column#NONE}, the {@link #filterKeyword} is cleared to {@code null}.
     *
     * @param c desired filter column
     */
    public void setFilterColumn(Column c) {
        this.filterColumn = (c == null ? Column.NONE : c);
        if (this.filterColumn == Column.NONE) {
            this.filterKeyword = null; // keep invariant: no keyword when no filter
        }
    }

    /**
     * @return the filter keyword, or {@code null} if none (or if {@link #filterColumn} is {@link Column#NONE})
     */
    public String getFilterKeyword() { return filterKeyword; }

    /**
     * Sets the filter keyword. Blank values are normalized to {@code null}.
     * <p>Note: if {@link #filterColumn} is {@link Column#NONE}, this value is ignored at apply-time
     * and may be cleared by {@link #setFilterColumn(Column)} in later calls.</p>
     *
     * @param kw keyword text (case/format handling is done by the caller)
     */
    public void setFilterKeyword(String kw) {
        this.filterKeyword = (kw == null || kw.isBlank()) ? null : kw;
    }

    /** @return current sort column (never {@code null}) */
    public Column getSortColumn() { return sortColumn; }

    /**
     * Sets the sort column. {@code null} is normalized to {@link Column#NONE}.
     * Sorting order is ascending by design (see service layer).
     *
     * @param c desired sort column
     */
    public void setSortColumn(Column c) {
        this.sortColumn = (c == null ? Column.NONE : c);
    }
}
