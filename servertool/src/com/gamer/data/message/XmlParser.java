package com.gamer.data.message;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class XmlParser {
    public static String DIR_PATH;

    static {
        if (!Util.inVM()) {
            File baseDir = new File(System.getProperty("user.dir"));
            baseDir = baseDir.getParentFile().getParentFile();
            DIR_PATH = baseDir.getPath() + "/Common/Tools/Bin/opcode" + File.separator;
        } else {
            DIR_PATH = System.getProperty("user.dir") + File.separator;
        }
    }

    public XmlParser() {}

    public static List<NetOpcode> parseXmls(String[] xmls) throws Exception {
        List<NetOpcode> netOpcodes = new ArrayList<>();
        if (xmls != null) {

            for (String xml : xmls) {
                String xmlFile = DIR_PATH + xml;
                NetOpcode netOpcode = parseXml(xmlFile);
                netOpcodes.add(netOpcode);
            }

        }
        return netOpcodes;
    }

    public static NetOpcode parseXml(String xmlFile) throws Exception {
        return parseXml(Files.newInputStream(Paths.get(xmlFile)));
    }

    public static NetOpcode parseXml(InputStream in) throws Exception {
        NetOpcode no;

        try {
            SAXReader reader = new SAXReader();
            reader.setEncoding("UTF-8");
            Document document = reader.read(in);
            Element root = document.getRootElement();
            no = parseRoot(root);
            parseElement(no, root);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }

        }

        return no;
    }

    private static NetOpcode parseRoot(Element root) {
        String commandType = root.attribute("commandType").getValue();
        String actionPack = root.attribute("actionPack").getValue();
        String enumPack = root.attribute("enumPack").getValue();
        String enumClass = root.attribute("enumClass").getValue();
        String actionXml = null;
        Attribute actionXmlAttr = root.attribute("actionXml");
        if (actionXmlAttr != null) {
            String actXmlVal = actionXmlAttr.getValue();
            if (actXmlVal != null && !actXmlVal.trim().isEmpty()) {
                actionXml = actXmlVal;
            }
        }

        return new NetOpcode(commandType, actionPack, enumPack, enumClass, actionXml);
    }

    private static void parseElement(NetOpcode no, Element root) {
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Iterator<Element> it = root.elementIterator("code");

        while (it.hasNext()) {
            Element element = it.next();
            int id = Integer.parseInt(element.attributeValue("id"));
            if (ids.contains(id)) {
                throw new IllegalArgumentException("Duplicate id definition. id:" + id);
            }

            String name = element.attributeValue("name");
            if (names.contains(name)) {
                throw new IllegalArgumentException("Duplicate name definition. name:" + name);
            }

            ids.add(id);
            names.add(name);
            Opcode code = new Opcode(id, name, element.attributeValue("label"));
            no.addOpcode(code);
        }

    }
}
