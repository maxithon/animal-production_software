package rw.animalproduct.animal.production.notification;

public enum LivestockLifecycleStage {
    YOUNG,
    PRE_BREEDING,
    READY_TO_BREED,
    MAX_AGE_WARNING,
    RETIREMENT,
    NOT_APPLICABLE // no category, no birthDate, or category missing age config
}