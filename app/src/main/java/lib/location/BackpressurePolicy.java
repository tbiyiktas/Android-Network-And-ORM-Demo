package lib.location;

/** Kuyruk doluyken ne yapılacağını belirler. */
public enum BackpressurePolicy {
    /** En eski sıradaki işi düşür, yeni işi sıraya al. */
    DROP_OLDEST,
    /** Yeni gelen işi düşür (sessizce). */
    DROP_LATEST,
    /** Yeni işi çağıran thread üzerinde hemen çalıştır. */
    RUN_ON_CALLER,
    /** İstisna fırlat (RejectedExecutionException). */
    ABORT
}