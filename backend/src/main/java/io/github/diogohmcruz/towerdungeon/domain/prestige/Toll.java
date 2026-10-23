package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * The full breakdown of a Toll.
 *
 * @param foodMarks Marks from Food grown
 * @param materialsMarks Marks from Materials banked
 * @param creditsMarks Marks from Credits banked
 * @param relicsMarks Marks from Relics banked
 * @param expeditionsMarks Marks from Expeditions completed
 * @param ascentMarks Marks from the Highest Floor above the baseline
 * @param guardianMarks Marks from Guardians slain
 * @param marks the sum of all Marks (S)
 * @param toll the Toll (T): Marks scaled by Load, plus Residue
 * @param newGround Guardians beyond the lifetime best (NG)
 * @param legacy the Legacy gained (P)
 * @param residueNext the remainder carried into the next Toll
 */
public record Toll(
    double foodMarks,
    double materialsMarks,
    double creditsMarks,
    double relicsMarks,
    double expeditionsMarks,
    double ascentMarks,
    double guardianMarks,
    double marks,
    double toll,
    int newGround,
    int legacy,
    double residueNext) {}
