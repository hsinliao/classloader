package com.hsin.util;

import java.io.*;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * native2ascii 工具的 Java 实现（增强版）
 * <p>
 * 功能：
 * - 将普通文本（含中文等非 ASCII 字符）转换为纯 ASCII 转义形式（\\uXXXX）。
 * - 将包含 \\uXXXX 转义序列的 ASCII 文本还原为普通文本。
 * - 智能处理：输入中已有的 \\uXXXX 序列在转换时会被保留，不会被二次转义。
 * - 占位符（如 {0}、{1}、{}）均为 ASCII 字符，不受影响。
 * <p>
 * 支持文件批量转换，可指定编码。
 */
public class Native2AsciiUtil {

    private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private Native2AsciiUtil() {
        // 工具类，禁止实例化
    }

    // ========================== 字符串转换 ==========================

    /**
     * 将普通字符串转换为 ASCII 转义形式（类似 native2ascii 命令）。
     * 规则：
     * - 所有非 ASCII 字符（> 0x7F）会被转换为 \\uXXXX 格式。
     * - 输入中已经存在的 \\uXXXX 转义序列（如 "\\u4E2D"）会被原样保留，不会被二次转义。
     * - ASCII 范围内的字符（包括占位符 {} 和数字）保持不变。
     *
     * @param input 原始字符串（可为 null）
     * @return 转换后的 ASCII 字符串，若输入为 null 返回 null
     */
    public static String stringToAscii(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length() * 2);
        Matcher matcher = UNICODE_ESCAPE_PATTERN.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            // 将匹配之前的普通部分追加到结果
            if (matcher.start() > lastEnd) {
                appendAsciiEscaped(input.substring(lastEnd, matcher.start()), sb);
            }
            // 直接保留匹配到的 \\uXXXX 序列
            sb.append(matcher.group());
            lastEnd = matcher.end();
        }
        // 处理剩余部分
        if (lastEnd < input.length()) {
            appendAsciiEscaped(input.substring(lastEnd), sb);
        }
        return sb.toString();
    }

    /**
     * 将字符串中的非 ASCII 字符转义为 \\uXXXX，ASCII 字符原样保留。
     * 辅助方法，用于普通文本的转义（不含已存在的 \\u 序列）。
     */
    private static void appendAsciiEscaped(String text, StringBuilder sb) {
        for (char c : text.toCharArray()) {
            if (c <= 0x7F) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04X", (int) c));
            }
        }
    }

    /**
     * 将包含 Unicode 转义序列的字符串还原为普通文本（类似 native2ascii -reverse）。
     * 例如："\\u4E2D\\u6587" -> "中文"
     * 注意：占位符（如 {0}）不会被此过程影响。
     *
     * @param input 包含 \\u 转义序列的字符串（可为 null）
     * @return 还原后的普通字符串，若输入为 null 返回 null
     */
    public static String asciiToString(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = UNICODE_ESCAPE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer(input.length());
        while (matcher.find()) {
            String hex = matcher.group(1);
            int codePoint = Integer.parseInt(hex, 16);
            matcher.appendReplacement(sb, String.valueOf((char) codePoint));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ========================== 文件转换 ==========================

    /**
     * 将输入文件转换为输出文件，支持“普通 → ASCII”或“ASCII → 普通”两种模式。
     *
     * @param srcFile  源文件
     * @param destFile 目标文件（若存在会被覆盖）
     * @param encoding 文件编码（如 "UTF-8"）
     * @param toAscii   true 表示将源文件中的非 ASCII 转为 \\u（native2ascii 模式）；
     *                  false 表示将源文件中的 \\u 转义还原为普通字符（-reverse 模式）
     * @throws IOException 读写异常
     */
    public static void convertFile(File srcFile, File destFile, String encoding, boolean toAscii)
            throws IOException {
        if (srcFile == null || destFile == null || encoding == null) {
            throw new IllegalArgumentException("参数不能为 null");
        }

        Charset charset = Charset.forName(encoding);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(srcFile), charset));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile), charset))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String convertedLine = toAscii ? stringToAscii(line) : asciiToString(line);
                writer.write(convertedLine);
                writer.newLine();
            }
        }
    }

    /**
     * 重载方法，使用默认 UTF-8 编码进行文件转换。
     */
    public static void convertFile(File srcFile, File destFile, boolean toAscii) throws IOException {
        convertFile(srcFile, destFile, "UTF-8", toAscii);
    }

    // ========================== 简易命令行入口（可选）======================

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        try {
            String mode = args[0];
            if ("-toascii".equals(mode) || "-totext".equals(mode)) {
                if (args.length < 3) {
                    printUsage();
                    return;
                }
                boolean toAscii = "-toascii".equals(mode);
                File src = new File(args[1]);
                File dest = new File(args[2]);
                String encoding = args.length >= 4 ? args[3] : "UTF-8";

                convertFile(src, dest, encoding, toAscii);
                System.out.println("转换完成：" + dest.getAbsolutePath());
            } else {
                // 简单测试：将传入的参数视为字符串进行转换
                System.out.println("原始字符串: " + args[0]);
                System.out.println("-> ASCII:   " + stringToAscii(args[0]));
                System.out.println("-> 还原后:   " + asciiToString(stringToAscii(args[0])));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.err.println("用法：");
        System.err.println("  转换为 ASCII: java Native2AsciiUtil -toascii 输入文件 输出文件 [编码]");
        System.err.println("  转换为文本:   java Native2AsciiUtil -totext 输入文件 输出文件 [编码]");
        System.err.println("  简单测试:     java Native2AsciiUtil \"要转换的字符串\"");
    }
}
