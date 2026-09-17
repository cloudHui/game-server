package com.gamer.data.message;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetOpcodeMaker {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static final String[] OPCODE_XMLS =
        new String[] {"ActionEnum_C2S.xml", "ActionEnum_S2C.xml", "ActionEnum_S2S.xml"};

    public NetOpcodeMaker() {}

    public static void main(String[] args) throws Exception {
        String xmlList = System.getProperty("xmlList");
        File xmls;
        if (Util.isNotBlank(xmlList)) {
            xmls = new File(XmlParser.DIR_PATH + xmlList);
            if (!xmls.exists() || !xmls.isFile()) {
                System.err.println("参数错误. xmlList:" + xmlList + ", 文件不存在.");
                System.exit(-1);
            }
        } else if (args.length < 1) {
            System.err.println("参数错误. arg0:行为枚举名称, arg1~argN:行为文件.");
            System.exit(-1);
        }

        String[] sxmls;
        if (Util.isBlank(xmlList)) {
            sxmls = Arrays.copyOfRange(OPCODE_XMLS, 0, OPCODE_XMLS.length);
        } else {
            sxmls = getActionEnumXml(xmlList);
        }

        List<NetOpcode> netOpcodes = XmlParser.parseXmls(sxmls);
        generateClazz(netOpcodes);
        generateConst(netOpcodes);
        generateXml(netOpcodes);

        if (!Util.inVM()) {
            // 复制*.java 和 *.xml 文件到指定目录，并删除当前目录下的*.java 和 *.xml 文件
            copyDeleteFile();
        }
    }

    /**
     * 复制 *.java 和 *.xml 文件到指定目录，并删除当前目录下的这些文件
     */
    private static void copyDeleteFile() {
        File srcDir = new File(System.getProperty("user.dir"));
        File baseDir = srcDir.getParentFile();

        // 定义需要处理的文件类型和目标目录
        Map<String, String> fileTypes = new HashMap<>();
        fileTypes.put("java", "/common/src/com/gow/common/net/action");
        fileTypes.put("xml", "/gameserver/src");

        for (Map.Entry<String, String> entry : fileTypes.entrySet()) {
            String extension = entry.getKey();
            String targetDir = entry.getValue();
            processFiles(srcDir, baseDir, extension, targetDir);
        }
    }

    /**
     * 处理指定扩展名的文件：复制到目标目录并删除源文件
     */
    private static void processFiles(File sourceDir, File baseDir, String extension, String targetPath) {
        try {
            File targetDir = new File(baseDir.getPath() + targetPath);

            // 确保目标目录存在
            if (!targetDir.exists()) {
                boolean mkdirs = targetDir.mkdirs();
                System.out.println("创建目标目录: " + targetDir.getPath() + " 结果: " + mkdirs);
            }

            // 
            File[] files = sourceDir.listFiles((dir, name) -> {
                name = name.toLowerCase();
                if(!name.contains("c2s") && !name.contains("s2s") && !name.contains("s2c")){
                    return false;
                }
                return name.endsWith("." + extension);
            });
            if (files == null) {
                return;
            }

            for (File sourceFile : files) {
                try {
                    Path sourcePath = sourceFile.toPath();
                    Path target = Paths.get(targetDir.getPath(), sourceFile.getName());
                    System.out.println("复制 " + extension + " 文件: " + sourceFile.getName());
                    // 复制文件，覆盖已存在的文件
                    Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
                    // 删除源文件
                    Files.delete(sourcePath);
                    System.out.println("已删除源文件: " + sourceFile.getName());
                } catch (Exception e) {
                    System.err.println("处理文件 " + sourceFile.getName() + " 失败: " + e);
                }
            }

        } catch (Exception e) {
            System.err.println("处理 " + extension + " 文件失败: " + e);
        }
    }

    static String[] getActionEnumXml(String xmlList) {
        ArrayList<String> lines = new ArrayList<>();
        File xlFile = new File(XmlParser.DIR_PATH + xmlList);

        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(Files.newInputStream(xlFile.toPath()), StandardCharsets.UTF_8))) {

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (Util.isNotBlank(line) && line.endsWith(".xml")) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            System.err.println("获取行为枚举XML失败: " + e);
            System.exit(-1);
        }

        String[] xmls = new String[lines.size()];
        lines.toArray(xmls);
        return xmls;
    }

    static void generateClazz(List<NetOpcode> netOpcodes) throws Exception {
        if (netOpcodes != null && !netOpcodes.isEmpty()) {

            for (NetOpcode netOpcode : netOpcodes) {
                String fileName = netOpcode.getEnumClass() + ".java";
                File f = new File(fileName);
                PrintWriter pw = new PrintWriter(f, StandardCharsets.UTF_8.name());
                String head = buildEnumHead(netOpcode.getEnumPack(), netOpcode.getEnumClass());
                write(pw, head);
                if (netOpcode.getCodes() != null && !netOpcode.getCodes().isEmpty()) {
                    int i = 0;

                    for (int len = netOpcode.getCodes().size(); i < len; ++i) {
                        Opcode code = netOpcode.getCodes().get(i);
                        StringBuilder str = new StringBuilder();
                        str.append(buildEnumField(code));
                        if (i < len - 1) {
                            str.append(",").append(NetOpcode.LINE_SEP);
                        } else {
                            str.append(";").append(NetOpcode.LINE_SEP).append(NetOpcode.LINE_SEP);
                        }

                        write(pw, str.toString());
                    }
                }

                String tail = buildEnumTail(netOpcode.getEnumClass());
                write(pw, tail);
                pw.close();
            }

        }
    }

    static void generateConst(List<NetOpcode> netOpcodes) throws Exception {
        if (netOpcodes != null && !netOpcodes.isEmpty()) {

            String className;
            for (NetOpcode netOpcode : netOpcodes) {
                className = netOpcode.getEnumClass();
                if (!className.toLowerCase().contains("c2s")) {
                    continue;// 只生成c2s的常量
                }
                className += "Const";
                String fileName = className + ".java";
                File f = new File(fileName);
                PrintWriter pw = new PrintWriter(f, StandardCharsets.UTF_8.name());
                String head = buildConstHead(netOpcode.getEnumPack(), className);
                write(pw, head);
                if (netOpcode.getCodes() != null && !netOpcode.getCodes().isEmpty()) {
                    int i = 0;

                    for (int len = netOpcode.getCodes().size(); i < len; ++i) {
                        Opcode code = netOpcode.getCodes().get(i);
                        write(pw, buildConstField(code));
                        System.out.println(netOpcode.getActionXml() + "  " + code.getName() + " = " + code.getId()
                            + ", " + code.getLabel());
                    }
                }
                write(pw, "\n}");
                pw.close();
            }

        }
    }

    private static String buildEnumHead(String enumPack, String enumClass) {
        return "package " + enumPack + ";" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP
            + "import com.gamer.core.net.action.ActionEnum;" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP + "/**"
            + NetOpcode.LINE_SEP + " * " + enumClass + NetOpcode.LINE_SEP + " * " + NetOpcode.LINE_SEP
            + " * @author net-opcode" + NetOpcode.LINE_SEP + " * @date " + getDateTime() + NetOpcode.LINE_SEP + " */"
            + NetOpcode.LINE_SEP + "public enum " + enumClass + " implements ActionEnum" + " {" + NetOpcode.LINE_SEP
            + NetOpcode.LINE_SEP;
    }

    private static String buildConstHead(String enumPack, String enumClass) {
        return "package " + enumPack + ";" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP + "/**" + NetOpcode.LINE_SEP
            + " * " + enumClass + NetOpcode.LINE_SEP + " * " + NetOpcode.LINE_SEP + " * @author net-opcode"
            + NetOpcode.LINE_SEP + " * @date " + getDateTime() + NetOpcode.LINE_SEP + " */" + NetOpcode.LINE_SEP
            + "public class " + enumClass + " {" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP;
    }

    private static String buildEnumField(Opcode code) {
        String field =
            code.getName() + "(" + code.getId() + ", \"" + (code.getLabel() == null ? "" : code.getLabel()) + "\")";
        return "    " + "/** " + field + " */" + NetOpcode.LINE_SEP + "    " + field;
    }

    private static String buildConstField(Opcode code) {
        String field = "public static final int " + code.getName() + " = " + code.getId() + ";\n";
        return "    " + "/** " + code.getName() + "," + code.getId() + ", " + code.getLabel() + " */"
            + NetOpcode.LINE_SEP + "    " + field;
    }

    private static String buildEnumTail(String enumClass) {
        return "    " + enumClass + "(int id, String comment) {" + NetOpcode.LINE_SEP + "    " + "    "
            + "this.id = id;" + NetOpcode.LINE_SEP + "    " + "    " + "this.comment = comment;" + NetOpcode.LINE_SEP
            + "    " + "}" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP + "    " + "private final int id;" + NetOpcode.LINE_SEP
            + NetOpcode.LINE_SEP + "    " + "private final String comment;" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP + "    "
            + "@Override" + NetOpcode.LINE_SEP + "    " + "public int getId() {" + NetOpcode.LINE_SEP + "    " + "    "
            + "return id;" + NetOpcode.LINE_SEP + "    " + "}" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP + "    "
            + "@Override" + NetOpcode.LINE_SEP + "    " + "public String getComment() {" + NetOpcode.LINE_SEP + "    "
            + "    " + "return comment;" + NetOpcode.LINE_SEP + "    " + "}" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP
            + "    " + "@Override" + NetOpcode.LINE_SEP + "    " + "public String getNote() {" + NetOpcode.LINE_SEP
            + "    " + "    " + "return this.toString();" + NetOpcode.LINE_SEP + "    " + "}" + NetOpcode.LINE_SEP
            + NetOpcode.LINE_SEP + "}" + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP;
    }

    static void generateXml(List<NetOpcode> netOpcodes) throws Exception {
        if (!netOpcodes.isEmpty()) {

            for (NetOpcode netOpcode : netOpcodes) {
                generateXml(netOpcode);
            }

        }
    }

    static void generateXml(NetOpcode netOpcode) throws Exception {
        if (netOpcode.getActionXml() != null && !netOpcode.getActionXml().trim().isEmpty()
            && !"".equalsIgnoreCase(netOpcode.getActionXml().trim())) {
            String xmlFile = netOpcode.getActionXml() + ".xml";
            File f = new File(xmlFile);
            PrintWriter pw = new PrintWriter(f, "UTF-8");
            String head = buildXmlHead();
            write(pw, head);
            List<Opcode> codes = netOpcode.getCodes();
            if (codes != null && !codes.isEmpty()) {
                int i = 0;

                for (int len = codes.size(); i < len; ++i) {
                    Opcode code = codes.get(i);
                    write(pw, buildXmlField(netOpcode.getCommandType(), netOpcode.getActionPack(), code));
                }
            }

            String tail = buildXmlTail();
            write(pw, tail);
            pw.close();
        }
    }

    private static String buildXmlHead() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NetOpcode.LINE_SEP
            + "<beans xmlns=\"http://www.springframework.org/schema/beans\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + NetOpcode.LINE_SEP + "    "
            + "xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">"
            + NetOpcode.LINE_SEP + NetOpcode.LINE_SEP;
    }

    private static String buildXmlField(String commandType, String actionPack, Opcode code) {
        String beanId = commandType + "_" + code.getId();
        String beanClass = actionPack + "." + code.getName();
        String beanName = null;
        if (code.getLabel() != null && !code.getLabel().trim().isEmpty()
            && !"".equalsIgnoreCase(code.getLabel().trim())) {
            beanName = code.getLabel();
        }

        StringBuilder str = new StringBuilder();
        str.append("    ").append("<bean id=\"").append(beanId).append("\" class=\"").append(beanClass).append("\" ");
        if (beanName != null) {
            str.append("name=\"").append(beanName).append("\" ");
        }

        str.append("lazy-init=\"true\"").append(" />").append(NetOpcode.LINE_SEP);
        return str.toString();
    }

    private static String buildXmlTail() {
        return NetOpcode.LINE_SEP + "</beans>" + NetOpcode.LINE_SEP;
    }

    static void write(PrintWriter fo, String str) {
        fo.write(str);
    }

    public static synchronized String getDateTime() {
        return sdf.format(new Date());
    }

}
