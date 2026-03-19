package com.hsin.i18n;

import java.util.Locale;

public class LocaleContextHolder {

    private static final ThreadLocal<Locale> currentLocale = new ThreadLocal<>();

    public static void setLocale(Locale locale) {
        currentLocale.set(locale);
    }

    public static Locale getLocale() {
        Locale locale = currentLocale.get();
        return locale != null ? locale : new Locale("zh", "CN");
    }

    public static void clear() {
        currentLocale.remove();
    }
}
