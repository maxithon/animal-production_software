package rw.animalproduct.animal.production.util;

/**
 * Single source of truth for livestock survey metrics.
 *
 * Every report (Business Overview, Herd Movement, Dashboard) MUST call
 * these methods instead of rolling its own formula. Before this existed,
 * the three reports computed mortality and offtake three different ways —
 * same underlying data, three different published numbers. That is a
 * serious problem for donor reporting and audits.
 *
 * All formulas follow FAO (2018) "Guidelines for the preparation of
 * livestock sector reviews and analyses" — the standard reference used
 * by national livestock surveys and M&amp;E frameworks.
 *
 * Design notes:
 *  - Pure static utility class; no Spring bean, no state.
 *  - All return values rounded to 1 decimal place, to match how FAO
 *    reports rates. Matches Math.round(v * 10) / 10.0 pattern.
 *  - All methods defensively handle zero/negative denominators so callers
 *    never see NaN or Infinity in a report.
 */
public final class LivestockMetrics {

    private LivestockMetrics() {
        // utility class — not instantiable
    }

    /**
     * Mortality rate — the FAO standard indicator for herd health.
     *
     * <p>Formula: {@code (deaths / opening stock) × 100}
     *
     * <p>Uses <b>opening stock</b> as the denominator, NOT the current herd
     * size. This is the definition used in every national livestock survey
     * and every FAO country brief. It matters: dividing by current stock
     * would inflate the rate whenever the herd has shrunk and understate
     * it whenever the herd has grown.
     *
     * @param dead    number of animals that died in the period
     * @param opening number of animals present at the start of the period
     * @return rate in percent (e.g. 4.2 means 4.2%), or 0.0 if opening ≤ 0
     */
    public static double mortalityRate(long dead, long opening) {
        if (opening <= 0) return 0.0;
        return round1(dead * 100.0 / opening);
    }

    /**
     * Offtake rate — the FAO standard indicator for stock outflow.
     *
     * <p>Formula: {@code ((sold + dead) / opening stock) × 100}
     *
     * <p>"Offtake" in FAO terminology means any animal that leaves the herd,
     * whether by sale or by death. Some sources use "sold only"; FAO's
     * Livestock Sector Briefs consistently use the inclusive definition.
     * Using the inclusive form also makes the metric reconcilable against
     * the herd-movement equation (closing = opening + in − offtake).
     *
     * @param sold    number of animals sold in the period
     * @param dead    number of animals that died in the period
     * @param opening number of animals present at the start of the period
     * @return rate in percent, or 0.0 if opening ≤ 0
     */
    public static double offtakeRate(long sold, long dead, long opening) {
        if (opening <= 0) return 0.0;
        return round1((sold + dead) * 100.0 / opening);
    }

    /**
     * Replacement rate — how much the herd is replacing itself by birth.
     *
     * <p>Formula: {@code (births / opening stock) × 100}
     *
     * <p>A rate at or above the mortality rate suggests the herd is
     * self-sustaining without purchases. Below that, external purchases
     * are required to maintain herd size.
     *
     * @param births  number of animals born in the period
     * @param opening number of animals present at the start of the period
     * @return rate in percent, or 0.0 if opening ≤ 0
     */
    public static double replacementRate(long births, long opening) {
        if (opening <= 0) return 0.0;
        return round1(births * 100.0 / opening);
    }

    /**
     * Herd growth rate — the net change in herd size over the period.
     *
     * <p>Formula: {@code ((closing − opening) / opening) × 100}
     *
     * <p>Positive means the herd is growing; negative means it is shrinking.
     * If opening = 0 (new farm or new category) and closing > 0, returns
     * 100.0 as a neutral "grew from nothing" marker rather than NaN.
     *
     * @param closing number of animals present at the end of the period
     * @param opening number of animals present at the start of the period
     * @return rate in percent
     */
    public static double herdGrowthRate(long closing, long opening) {
        if (opening <= 0) {
            return closing > 0 ? 100.0 : 0.0;
        }
        return round1((closing - opening) * 100.0 / opening);
    }

    /**
     * Birth rate — same definition as replacement rate in FAO terminology,
     * provided as a separately-named method for reports that label the
     * metric "Birth Rate" rather than "Replacement Rate".
     */
    public static double birthRate(long births, long opening) {
        return replacementRate(births, opening);
    }

    /* ================================================================
     *  Internals
     * ================================================================ */

    /** Round to 1 decimal place — FAO rate convention. */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}