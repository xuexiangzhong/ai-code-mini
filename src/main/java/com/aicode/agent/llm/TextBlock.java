package com.aicode.agent.llm;

public record TextBlock(String text) implements ContentBlock {
    public TextBlock {
        if (text == null) {
            text = "";
        }
    }

    public String type() {
        return "text";
    }
}
