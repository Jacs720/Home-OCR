package com.baidu.paddle.lite.demo.ocr;

public final class ChecklistEntry {
    public final String id;
    public final int nationalNumber;
    public final String pokemon;
    public final String form;
    public final ChecklistMark mark;
    public final boolean shiny;
    public boolean owned;

    public ChecklistEntry(
            String id,
            int nationalNumber,
            String pokemon,
            String form,
            ChecklistMark mark,
            boolean shiny
    ) {
        this.id = id;
        this.nationalNumber = nationalNumber;
        this.pokemon = pokemon;
        this.form = form;
        this.mark = mark;
        this.shiny = shiny;
    }
}
