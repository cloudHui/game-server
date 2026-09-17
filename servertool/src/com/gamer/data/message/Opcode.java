package com.gamer.data.message;

import java.io.Serializable;

public class Opcode implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private final String name;
    private final String label;

    public Opcode(int id, String name, String label) {
        this.id = id;
        this.name = name;
        this.label = label;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getLabel() {
        return this.label;
    }
}