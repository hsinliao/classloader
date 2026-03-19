package com.hsin.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class I18n {

    // 缓存 ResourceBundle，键为 basename + "_" + locale.toString()
    private static final ConcurrentHashMap<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();

    private I18n() {}

    /**
     * 获取当前线程 Locale 对应的消息
     */
    public static String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return getMessage(key, locale, args);
    }

    /**
     * 获取指定 Locale 的消息
     */
    public static String getMessage(String key, Locale locale, Object... args) {
        String value = getStringOrNull(key, locale);
        if (value == null) {
            return key; // 未找到时返回 key 本身
        }
        if (args != null && args.length > 0) {
            return MessageFormat.format(value, args);
        }
        return value;
    }

    /**
     * 获取消息，支持默认值（当 key 不存在时返回 defaultMessage）
     */
    public static String getMessage(String key, String defaultMessage, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        String value = getStringOrNull(key, locale);
        if (value == null) {
            return defaultMessage != null ? defaultMessage : key;
        }
        if (args != null && args.length > 0) {
            return MessageFormat.format(value, args);
        }
        return value;
    }

    private static String getStringOrNull(String key, Locale locale) {
        ResourceBundle bundle = getBundle("i18n/messages", locale);
        if (bundle.containsKey(key)) {
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                // ignore
            }
        }
        return null;
    }

    private static ResourceBundle getBundle(String basename, Locale locale) {
        String cacheKey = basename + "_" + locale.toString();
        ResourceBundle bundle = bundleCache.get(cacheKey);
        if (bundle == null) {
            // 使用自定义 Control 以支持 UTF-8（Java 8 及以下）
            // ResourceBundle.Control control = new UTF8Control();
            // bundle = ResourceBundle.getBundle(basename, locale, control);

            // Java 9+ 直接支持 UTF-8，使用默认加载即可
            bundle = ResourceBundle.getBundle(basename, locale);
            bundleCache.put(cacheKey, bundle);
        }
        return bundle;
    }
}
