package com.gamer.data.gdg.util;

public enum DataType {
    TYPE_V_IDX("vindex", "v"),
    TYPE_INT("int", "i"),
    TYPE_FLOAT("float", "f"),
    TYPE_STR("string", "s");

    private String name;
    private final String shortName;

    DataType(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public static DataType parseName(String name) {
        for(DataType type : values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }

        return null;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return this.shortName;
    }

}
