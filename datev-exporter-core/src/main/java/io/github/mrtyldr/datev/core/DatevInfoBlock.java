package io.github.mrtyldr.datev.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A slot pool for one of DATEV's repeating {@code Art}/{@code Inhalt} groups.
 *
 * <p>{@code Beleginfo} offers eight numbered slots and {@code Zusatzinformation} twenty. Filling
 * them by hand means tracking which slot is still free and reproducing headings such as
 * {@code "Zusatzinformation- Inhalt 3"} exactly. This class takes labelled entries instead and
 * assigns slots in insertion order:
 *
 * <pre>{@code
 * var extras = DatevInfoBlock.additionalInfo()
 *         .put("Auftragsnr", "A-4711")
 *         .put("Kostenträger", "KT-9");
 * file.appendColumns(extras.toColumns());
 * }</pre>
 *
 * <p>Entries are length-checked against the official field definition as they are added, so an
 * over-long label fails at the call site rather than during row validation. Only the occupied
 * slots produce columns; the remaining ones stay empty in the exported row.
 *
 * <p>Instances are mutable and not thread-safe. {@link #toColumns()} returns an independent
 * snapshot, so a block may be reused across rows and modified between them.
 *
 * @see DatevField.Group
 */
public final class DatevInfoBlock {
    private final DatevField.Group group;
    private final Map<String, String> entries = new LinkedHashMap<>();

    private DatevInfoBlock(DatevField.Group group) {
        this.group = group;
    }

    /**
     * Creates an empty {@code Beleginfo} block with eight slots.
     *
     * @return a new empty block
     */
    public static DatevInfoBlock documentInfo() {
        return new DatevInfoBlock(DatevField.Group.DOCUMENT_INFO);
    }

    /**
     * Creates an empty {@code Zusatzinformation} block with twenty slots.
     *
     * @return a new empty block
     */
    public static DatevInfoBlock additionalInfo() {
        return new DatevInfoBlock(DatevField.Group.ADDITIONAL_INFO);
    }

    /**
     * Creates an empty block for an explicit group.
     *
     * @param group the repeating group to fill
     * @return a new empty block
     */
    public static DatevInfoBlock of(DatevField.Group group) {
        return new DatevInfoBlock(Objects.requireNonNull(group, "group"));
    }

    /**
     * Adds an entry to the next free slot.
     *
     * @param type the {@code Art} label; must not be blank
     * @param content the {@code Inhalt} value; may be empty but not {@code null}
     * @return this block, for chaining
     * @throws IllegalStateException if every slot is already occupied
     * @throws IllegalArgumentException if the label is blank, already present, or either value
     *     exceeds the official field length
     */
    public DatevInfoBlock put(String type, String content) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(content, "content");
        if (type.isBlank()) {
            throw new IllegalArgumentException(
                    "DATEV " + group.name() + " label must not be blank.");
        }
        if (entries.containsKey(type)) {
            throw new IllegalArgumentException("DATEV " + group.name()
                    + " already contains the label '" + type + "'.");
        }
        if (entries.size() == group.slotCount()) {
            throw new IllegalStateException("DATEV " + group.name() + " offers only "
                    + group.slotCount() + " slots; cannot add '" + type + "'.");
        }
        int slotNumber = entries.size() + 1;
        checkLength(group.field(slotNumber, DatevField.Part.TYPE), type);
        checkLength(group.field(slotNumber, DatevField.Part.CONTENT), content);
        entries.put(type, content);
        return this;
    }

    /**
     * Adds every entry of a map, in the map's iteration order.
     *
     * @param values labels mapped to their contents
     * @return this block, for chaining
     * @throws IllegalStateException if the entries do not fit into the free slots
     * @throws IllegalArgumentException if any entry is rejected by {@link #put(String, String)}
     */
    public DatevInfoBlock putAll(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(values).entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Returns the group this block fills.
     *
     * @return the repeating group
     */
    public DatevField.Group group() {
        return group;
    }

    /**
     * Returns how many slots are occupied.
     *
     * @return the number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns how many slots the group offers in total.
     *
     * @return 8 for {@code Beleginfo}, 20 for {@code Zusatzinformation}
     */
    public int capacity() {
        return group.slotCount();
    }

    /**
     * Returns how many slots are still free.
     *
     * @return {@code capacity() - size()}
     */
    public int remaining() {
        return capacity() - size();
    }

    /**
     * Returns whether no slot is occupied.
     *
     * @return {@code true} if this block has no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns whether a label is already present.
     *
     * @param type the {@code Art} label to look for
     * @return {@code true} if the label occupies a slot
     */
    public boolean contains(String type) {
        return type != null && entries.containsKey(type);
    }

    /**
     * Returns the entries in slot order.
     *
     * @return an unmodifiable snapshot mapping each label to its content, in slot order
     */
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * Converts the occupied slots into columns.
     *
     * <p>The result contains two columns per entry — the {@code Art} label and its
     * {@code Inhalt} content — in official output order. Free slots produce no columns and stay
     * empty in the exported row.
     *
     * @return a new immutable list of {@code 2 * size()} columns
     */
    public List<DatevColumn<String>> toColumns() {
        var columns = new ArrayList<DatevColumn<String>>(2 * entries.size());
        int slotNumber = 1;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            columns.add(DatevColumn.of(
                    group.field(slotNumber, DatevField.Part.TYPE), entry.getKey()));
            columns.add(DatevColumn.of(
                    group.field(slotNumber, DatevField.Part.CONTENT), entry.getValue()));
            slotNumber++;
        }
        return List.copyOf(columns);
    }

    /**
     * Returns a diagnostic description of this block.
     *
     * @return the group name with the slot usage
     */
    @Override
    public String toString() {
        return "DatevInfoBlock[" + group.name() + ' ' + size() + '/' + capacity() + ']';
    }

    private static void checkLength(DatevField field, String value) {
        int maxLength = field.spec().maxLength();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Value for '" + field.heading() + "' is "
                    + value.length() + " characters but DATEV allows " + maxLength + '.');
        }
    }
}
