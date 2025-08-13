package app.model;

import lib.persistence.annotations.DbColumnAnnotation;
import lib.persistence.annotations.DbTableAnnotation;

@DbTableAnnotation( name = "BtDevices")
public class BtDevice {
    @DbColumnAnnotation(isIdentity = true, isPrimaryKey = true,isNullable = false,ordinal = 1)
    public int id;
    @DbColumnAnnotation(isNullable = false, ordinal = 2)
    public String name;
    @DbColumnAnnotation(isNullable = false,ordinal = 3)
    public  String address;
}
