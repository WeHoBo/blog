package com.blog.front.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.InputStream;
import java.util.List;

public class DocxToMdUtil {

    public static String convert(InputStream inputStream) throws Exception {
        XWPFDocument doc = new XWPFDocument(inputStream);
        StringBuilder md = new StringBuilder();
        List<IBodyElement> elements = doc.getBodyElements();

        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                String style = para.getStyle();
                String text = para.getText().trim();
                if (text.isEmpty()) {
                    md.append("\n");
                    continue;
                }

                if (style != null && style.startsWith("Heading")) {
                    int level = style.charAt(7) - '0';
                    md.append("#".repeat(Math.min(level, 6))).append(" ").append(text).append("\n\n");
                } else {
                    // Bold and italic inline formatting
                    StringBuilder line = new StringBuilder();
                    for (XWPFRun run : para.getRuns()) {
                        String runText = run.text();
                        if (runText == null) continue;
                        if (run.isBold() && run.isItalic()) {
                            line.append("**_").append(runText).append("_**");
                        } else if (run.isBold()) {
                            line.append("**").append(runText).append("**");
                        } else if (run.isItalic()) {
                            line.append("*").append(runText).append("*");
                        } else {
                            line.append(runText);
                        }
                    }
                    md.append(line.toString().trim()).append("\n\n");
                }
            } else if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                for (int i = 0; i < table.getRows().size(); i++) {
                    XWPFTableRow row = table.getRow(i);
                    md.append("| ");
                    row.getTableCells().forEach(cell -> md.append(cell.getText().trim()).append(" | "));
                    md.append("\n");
                    if (i == 0) {
                        md.append("| ");
                        row.getTableCells().forEach(cell -> md.append("--- | "));
                        md.append("\n");
                    }
                }
                md.append("\n");
            }
        }
        doc.close();
        return md.toString().trim();
    }
}
