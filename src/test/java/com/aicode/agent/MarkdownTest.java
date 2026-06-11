package com.aicode.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.aicode.agent.Markdown.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownTest {
    @Nested
    class TestStripAnsi {
        @Test
        void removeAnsi() {
            assertEquals("hello", stripAnsi(BOLD + "hello" + RESET));
        }

        @Test
        void plainText() {
            assertEquals("hello", stripAnsi("hello"));
        }
    }

    @Nested
    class TestRenderInline {
        @Test
        void bold() {
            String result = renderInline("this is **bold** text");
            assertTrue(result.contains(BOLD));
            assertEquals("this is bold text", stripAnsi(result));
        }

        @Test
        void boldUnderscore() {
            assertEquals("this is bold text", stripAnsi(renderInline("this is __bold__ text")));
        }

        @Test
        void inlineCode() {
            String result = renderInline("use `console.log`");
            assertTrue(result.contains(CYAN));
            assertEquals("use console.log", stripAnsi(result));
        }

        @Test
        void italic() {
            String result = renderInline("this is *italic* text");
            assertTrue(result.contains(ITALIC));
            assertEquals("this is italic text", stripAnsi(result));
        }

        @Test
        void mixed() {
            String plain = stripAnsi(renderInline("**bold** and `code`"));
            assertTrue(plain.contains("bold"));
            assertTrue(plain.contains("code"));
        }

        @Test
        void plainText() {
            assertEquals("no formatting", renderInline("no formatting"));
        }
    }

    @Nested
    class TestRenderCodeBlock {
        @Test
        void withLanguage() {
            String plain = stripAnsi(renderCodeBlock("const x = 1;", "typescript"));
            assertTrue(plain.contains("typescript"));
            assertTrue(plain.contains("const x = 1;"));
            assertTrue(plain.contains("┌"));
            assertTrue(plain.contains("└"));
        }

        @Test
        void multiline() {
            String plain = stripAnsi(renderCodeBlock("line1\nline2\nline3", ""));
            assertTrue(plain.contains("line1"));
            assertTrue(plain.contains("line2"));
            assertTrue(plain.contains("line3"));
        }

        @Test
        void noLanguage() {
            assertTrue(stripAnsi(renderCodeBlock("code", "")).contains("code"));
        }
    }

    @Nested
    class TestRenderHeading {
        @Test
        void h1() {
            String result = renderHeading("Title", 1);
            assertTrue(result.contains(MAGENTA));
            assertTrue(stripAnsi(result).contains("# Title"));
        }

        @Test
        void h2() {
            String result = renderHeading("Section", 2);
            assertTrue(result.contains(GREEN));
            assertTrue(stripAnsi(result).contains("## Section"));
        }

        @Test
        void h3() {
            String result = renderHeading("Sub", 3);
            assertTrue(result.contains(YELLOW));
            assertTrue(stripAnsi(result).contains("### Sub"));
        }
    }

    @Nested
    class TestRenderListItem {
        @Test
        void bullet() {
            assertTrue(stripAnsi(renderListItem("item text", 0)).contains("• item text"));
        }

        @Test
        void indent() {
            assertTrue(stripAnsi(renderListItem("nested", 4)).startsWith("    •"));
        }

        @Test
        void inlineFormatting() {
            assertTrue(stripAnsi(renderListItem("**bold** item", 0)).contains("bold item"));
        }
    }

    @Nested
    class TestRenderHorizontalRule {
        @Test
        void dashes() {
            assertTrue(stripAnsi(renderHorizontalRule()).contains("─".repeat(48)));
        }
    }

    @Nested
    class TestRenderMarkdown {
        @Test
        void headings() {
            String plain = stripAnsi(renderMarkdown("# Title\n\n## Section"));
            assertTrue(plain.contains("# Title"));
            assertTrue(plain.contains("## Section"));
        }

        @Test
        void codeBlocks() {
            String plain = stripAnsi(renderMarkdown("```typescript\nconst x = 1;\n```"));
            assertTrue(plain.contains("typescript"));
            assertTrue(plain.contains("const x = 1;"));
        }

        @Test
        void unorderedLists() {
            String plain = stripAnsi(renderMarkdown("- item 1\n- item 2"));
            assertTrue(plain.contains("• item 1"));
            assertTrue(plain.contains("• item 2"));
        }

        @Test
        void orderedLists() {
            String plain = stripAnsi(renderMarkdown("1. first\n2. second"));
            assertTrue(plain.contains("• first"));
            assertTrue(plain.contains("• second"));
        }

        @Test
        void horizontalRules() {
            assertTrue(stripAnsi(renderMarkdown("---")).contains("─".repeat(48)));
        }

        @Test
        void inlineInParagraphs() {
            String plain = stripAnsi(renderMarkdown("This is **bold** and `code`"));
            assertTrue(plain.contains("bold"));
            assertTrue(plain.contains("code"));
        }

        @Test
        void emptyInput() {
            assertEquals("", renderMarkdown(""));
        }

        @Test
        void unclosedCodeBlock() {
            String plain = stripAnsi(renderMarkdown("```python\nprint('hi')"));
            assertTrue(plain.contains("print('hi')"));
        }

        @Test
        void preserveEmptyLines() {
            String[] parts = renderMarkdown("line1\n\nline2").split("\n");
            assertEquals(3, parts.length);
        }
    }
}
