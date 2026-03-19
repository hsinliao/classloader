package com.hsin.util;

public class Test {

    public static void main1(String[] args) {
        String text = "欢迎 {0}！已有转义：\\u4E2D\\u6587";
        String ascii = Native2AsciiUtil.stringToAscii(text);
        System.out.println(ascii);
        // 输出：\u6B22\u8FCE {0}\uFF01\u5DF2\u6709\u8F6C\u4E49：\u4E2D\u6587
        // 注意：原有的 \u4E2D\u6587 被原样保留，没有被二次转义

        String restored = Native2AsciiUtil.asciiToString(ascii);
        System.out.println(restored);
        // 输出：欢迎 {0}！已有转义：中文
    }

    public static void main(String[] args) {
        String text = "用户{0}于{1}登录成功，订单{}支付金额{2}元";
        String ascii = Native2AsciiUtil.stringToAscii(text);
        System.out.println(ascii);
        String restored = Native2AsciiUtil.asciiToString(ascii);
        System.out.println(restored);
    }
}
