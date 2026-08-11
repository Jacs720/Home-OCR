package com.baidu.paddle.lite.demo.ocr;

public final class ChecklistEntry {
    public static final String TYPE_ORIGIN_MARK = "ORIGIN_MARK";
    public static final String TYPE_DREAM_BALL = "DREAM_BALL";
    public static final String TYPE_COLO_XD = "COLO_XD";

    public final String id;
    public final int nationalNumber;
    public final String pokemon;
    public final String form;
    public final String originMark;
    public final String targetType;
    public final boolean ownedInitial;
    public boolean owned;

    public ChecklistEntry(
            String id,
            int nationalNumber,
            String pokemon,
            String form,
            String originMark,
            String targetType,
            boolean ownedInitial
    ) {
        this.id = id;
        this.nationalNumber = nationalNumber;
        this.pokemon = pokemon;
        this.form = form;
        this.originMark = originMark;
        this.targetType = targetType;
        this.ownedInitial = ownedInitial;
    }
}
