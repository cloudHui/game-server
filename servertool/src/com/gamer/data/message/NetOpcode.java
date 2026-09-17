package com.gamer.data.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NetOpcode implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String LINE_SEP = System.lineSeparator();
    private final String commandType;
    private final String actionPack;
    private final String enumPack;
    private final String enumClass;
    private String actionXml;
    private List<Opcode> codes;

    public NetOpcode(String commandType, String actionPack, String enumPack, String enumClass, String actionXml) {
        this.actionXml = null;
        this.codes = null;
        this.commandType = commandType;
        this.actionPack = actionPack;
        this.enumPack = enumPack;
        this.enumClass = enumClass;
        this.actionXml = actionXml;
        this.codes = new ArrayList<>();
    }

    public void addOpcode(Opcode code) {
        if (code != null && code.getId() > 0) {
            this.codes.add(code);
        }
    }

    public String getCommandType() {
        return this.commandType;
    }

    public String getActionPack() {
        return this.actionPack;
    }

    public String getEnumPack() {
        return this.enumPack;
    }

    public String getEnumClass() {
        return this.enumClass;
    }

    public String getActionXml() {
        return this.actionXml;
    }

    public List<Opcode> getCodes() {
        return this.codes;
    }


}
