package lib.location;


import java.util.UUID;

public class LocationSubscription {
    public final UUID id = UUID.randomUUID();
    volatile boolean active = true;
}