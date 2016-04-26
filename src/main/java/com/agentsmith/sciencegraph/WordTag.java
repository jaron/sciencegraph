package com.agentsmith.sciencegraph;


/** Stores the result of PoS Tagging */

public class WordTag
{
    public String text;
    public String tag;

    public WordTag(String text, String tag) {
        this.text = text.toLowerCase();
        this.tag = tag;
    }

    @Override
    public String toString() {
        return text + " (" + tag + ")";
    }
}
