package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReadyToBreedKpiDTO {
    /** Animals with all 4 checklist reasons met. */
    private long totalReadyToBreed;
    /** Animals flagged READY_TO_BREED but failing at least one checklist reason. */
    private long almostReady;

    // The following are scoped to READY (4/4) animals only — notification
    // workflow doesn't apply to animals that aren't actually ready yet.
    private long notified;
    private long awaitingNotification;
    private long notificationOverdue;   // not notified for 3+ days past readySince
    private long firstTimeBreeders;     // births == 0, among READY animals
    private double averageDaysReady;    // avg. days past readySince, among READY animals
}