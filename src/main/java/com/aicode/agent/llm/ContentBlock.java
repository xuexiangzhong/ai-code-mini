package com.aicode.agent.llm;

/**
 * Content blocks in LLM messages.
 */
public sealed interface ContentBlock permits TextBlock, ImageBlock, ToolUseBlock, ToolResultBlock {
}
