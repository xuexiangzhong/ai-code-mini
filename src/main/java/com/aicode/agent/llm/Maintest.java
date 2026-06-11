package com.aicode.agent.llm;

import com.aicode.agent.MessageHistory;

import java.util.List;

public class Maintest {
    public static String key = "";
    public static String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
    public static String model = "qwen3.7-max";
    public static void main(String[] args) {
        LLMProvider provider = ProviderFactory.createProvider(
                new ProviderFactory.ProviderConfig(
                        "openai-compatible", key, model, baseUrl));
        var response = provider.chat(List.of(Message.user("我之前问过什么问题 ")), new ChatOptions()).join();
        System.out.println(response.text());
        MessageHistory history = new MessageHistory();
        OpenAICompatibleProvider provider2 = new OpenAICompatibleProvider(
                new OpenAICompatibleProvider.Config(key, baseUrl, model));
        history.addUser("你是谁？");
        StringBuilder fullText = new StringBuilder();
        provider2.stream(List.of(Message.user("你是谁？")), null, event -> {
            if ("text_delta".equals(event.type()) && event.text() != null) {
                fullText.append(event.text());
                if(event.text() != null && event.text().length() > 0) {
//                    System.out.println(event.text());
                }
            }
        });
        System.out.println(fullText.toString());
        history.addAssistant(fullText.toString());
        history.addUser("我之前问过什么问题 ");
//        history.addAssistant("A2");
        response = provider.chat(history.getMessages(), new ChatOptions()).join();
        System.out.println(response.text());
    }
}
