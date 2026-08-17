package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;

import androidx.annotation.StringRes;

/** Stable origin-mark codes used by the checklist catalog and UI filters. */
public enum ChecklistMark {
    NO_MARK("NO_MARK", R.string.mark_no_mark),
    GB("GB", R.string.mark_gb),
    GO("GO", R.string.mark_go),
    P("P", R.string.mark_p),
    USUM("USUM", R.string.mark_usum),
    LGPE("LGPE", R.string.mark_lgpe),
    SWSH("SWSH", R.string.mark_swsh),
    LA("LA", R.string.mark_la),
    BDSP("BDSP", R.string.mark_bdsp),
    SV("SV", R.string.mark_sv),
    LZA("LZA", R.string.mark_lza);

    public final String code;
    @StringRes public final int labelResource;

    ChecklistMark(String code, @StringRes int labelResource) {
        this.code = code;
        this.labelResource = labelResource;
    }

    public String label(Context context) {
        return context.getString(labelResource);
    }

    public static ChecklistMark fromCode(String code) {
        for (ChecklistMark mark : values()) {
            if (mark.code.equalsIgnoreCase(code)) return mark;
        }
        return null;
    }

    public static ChecklistMark fromDetected(String value) {
        String mark = ChecklistMatcher.normalize(value);
        if (mark.contains("sin marca") || mark.contains("no mark")) return NO_MARK;
        if (mark.contains("consola virtual") || mark.contains("gameboy")
                || mark.contains("game boy") || mark.equals("gb")) return GB;
        if (mark.contains("pokemon go") || mark.equals("go") || mark.contains("go mark")) {
            return GO;
        }
        if (mark.contains("pentagon") || mark.equals("kalos") || mark.equals("p")) return P;
        if (mark.contains("clover") || mark.equals("alola") || mark.equals("usum")) return USUM;
        if (mark.contains("let s go") || mark.contains("lets go") || mark.equals("lgpe")) {
            return LGPE;
        }
        if (mark.equals("galar") || mark.contains("galar mark") || mark.equals("swsh")) {
            return SWSH;
        }
        if (mark.contains("hisui") || mark.contains("legends arceus")
                || mark.equals("pla") || mark.equals("la")) return LA;
        if (mark.contains("bdsp") || mark.contains("sinnoh")) return BDSP;
        if (mark.equals("paldea") || mark.contains("scarlet violet") || mark.equals("sv")) {
            return SV;
        }
        if (mark.contains("legends z a") || mark.equals("lza")) return LZA;
        return null;
    }
}
