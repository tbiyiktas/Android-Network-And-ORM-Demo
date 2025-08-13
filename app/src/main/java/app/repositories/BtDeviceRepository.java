package app.repositories;

import app.model.BtDevice;
import lib.persistence.ADbContext;
import lib.persistence.GenericRepository;

public class BtDeviceRepository extends GenericRepository<BtDevice> {
    public BtDeviceRepository(ADbContext dbContext) {
        super(dbContext, BtDevice.class);
    }
}
