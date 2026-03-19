package com.hsin.i18n;

public class Test {

    public static void main(String[] args) {
       String msg = I18n.getMessage("log.user.login","","hsin",System.currentTimeMillis());
       System.out.println(msg);
    }
}
