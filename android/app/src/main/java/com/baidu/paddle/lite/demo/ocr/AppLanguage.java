package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/** Languages exposed by Pokémon HOME and supported by the local species catalog. */
public enum AppLanguage {
    ES_ES("es-ES", "7", R.drawable.language_es_es, "Español (España)"),
    ES_LA("es-419", "7", R.drawable.language_es_la, "Español (Latinoamérica)"),
    ENGLISH("en", "9", R.drawable.language_en, "English"),
    FRENCH("fr", "5", R.drawable.language_fr, "Français"),
    ITALIAN("it", "8", R.drawable.language_it, "Italiano"),
    GERMAN("de", "6", R.drawable.language_de, "Deutsch"),
    JAPANESE("ja", "11", R.drawable.language_ja, "日本語"),
    CHINESE_TRADITIONAL("zh-Hant", "4", R.drawable.language_zh_hant, "繁體中文"),
    CHINESE_SIMPLIFIED("zh-Hans", "12", R.drawable.language_zh_hans, "简体中文"),
    KOREAN("ko", "3", R.drawable.language_ko, "한국어");

    private static final String PREFERENCES = "application_language";
    private static final String CONFIGURED = "configured";

    public final String languageTag;
    public final String catalogLanguageId;
    @DrawableRes public final int badgeResource;
    public final String displayName;

    AppLanguage(
            String languageTag,
            String catalogLanguageId,
            @DrawableRes int badgeResource,
            String displayName
    ) {
        this.languageTag = languageTag;
        this.catalogLanguageId = catalogLanguageId;
        this.badgeResource = badgeResource;
        this.displayName = displayName;
    }

    /** Makes Spanish the first-run default while preserving later user choices. */
    public static void ensureSpanishDefault(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        LocaleListCompat selected = AppCompatDelegate.getApplicationLocales();
        if (!selected.isEmpty()) {
            preferences.edit().putBoolean(CONFIGURED, true).apply();
            return;
        }
        if (!preferences.getBoolean(CONFIGURED, false)) {
            preferences.edit().putBoolean(CONFIGURED, true).apply();
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(ES_ES.languageTag));
        }
    }

    public static AppLanguage current(Context context) {
        LocaleListCompat selected = AppCompatDelegate.getApplicationLocales();
        if (!selected.isEmpty() && selected.get(0) != null) {
            return fromLocale(selected.get(0));
        }
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return fromLocale(locale);
    }

    public void apply(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CONFIGURED, true)
                .apply();
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag));
    }

    private static AppLanguage fromLocale(Locale locale) {
        if (locale == null) return ES_ES;
        String language = locale.getLanguage();
        if ("es".equals(language)) {
            String country = locale.getCountry();
            return "419".equals(country) || "MX".equals(country)
                    || "AR".equals(country) || "CL".equals(country)
                    || "CO".equals(country) || "PE".equals(country)
                    ? ES_LA : ES_ES;
        }
        if ("fr".equals(language)) return FRENCH;
        if ("it".equals(language)) return ITALIAN;
        if ("de".equals(language)) return GERMAN;
        if ("ja".equals(language)) return JAPANESE;
        if ("ko".equals(language)) return KOREAN;
        if ("zh".equals(language)) {
            String script = locale.getScript();
            String country = locale.getCountry();
            return "Hant".equalsIgnoreCase(script)
                    || "TW".equals(country) || "HK".equals(country) || "MO".equals(country)
                    ? CHINESE_TRADITIONAL : CHINESE_SIMPLIFIED;
        }
        if ("en".equals(language)) return ENGLISH;
        return ES_ES;
    }
}
