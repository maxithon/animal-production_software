package rw.animalproduct.animal.production.dto;

/**
 * One row in the "By Breeding Method" table on the breeding performance report.
 * Referenced as BreedingPerformanceReportDto.byMethod but wasn't defined anywhere,
 * which would have caused a compile error the moment the report controller tried
 * to populate it.
 */
public class BreedingMethodStatsRow {

    private String method;
    private int totalAttempts;
    private int confirmedPregnant;
    private int completed;
    private int failed;

    public BreedingMethodStatsRow() {
    }

    public BreedingMethodStatsRow(String method, int totalAttempts, int confirmedPregnant, int completed, int failed) {
        this.method = method;
        this.totalAttempts = totalAttempts;
        this.confirmedPregnant = confirmedPregnant;
        this.completed = completed;
        this.failed = failed;
    }

    public double getSuccessRatePercent() {
        if (totalAttempts == 0) return 0.0;
        return Math.round((confirmedPregnant + completed) * 1000.0 / totalAttempts) / 10.0;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(int totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public int getConfirmedPregnant() {
        return confirmedPregnant;
    }

    public void setConfirmedPregnant(int confirmedPregnant) {
        this.confirmedPregnant = confirmedPregnant;
    }

    public int getCompleted() {
        return completed;
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }
}
