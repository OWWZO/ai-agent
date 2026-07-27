package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiCatalog;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiExportService;

import java.util.List;
import java.util.Map;

public class GenUiExportServiceTest {

    @Test
    public void fullCatalogContainsLegacyAndNewKinds() {
        Assert.assertTrue(GenUiCatalog.isAllowedKind("Card"));
        Assert.assertTrue(GenUiCatalog.isAllowedKind("WeatherCard"));
        Assert.assertTrue(GenUiCatalog.isAllowedKind("ThreeJsFrame"));
        Assert.assertTrue(GenUiCatalog.isAllowedKind("SlideDeck"));
        Assert.assertTrue(GenUiCatalog.listCatalog().size() >= 50);
    }

    @Test
    public void exportPdfAndDocx() {
        Map<String, Object> tree = Map.of(
                "kind", "Stack",
                "children", List.of(
                        Map.of("kind", "Heading", "props", Map.of("level", 1, "value", "Report")),
                        Map.of("kind", "Text", "props", Map.of("value", "Hello export")),
                        Map.of("kind", "Stat", "props", Map.of("label", "KPI", "value", "42", "delta", "+3%"))
                )
        );
        byte[] pdf = GenUiExportService.exportPdf(tree, "document");
        byte[] docx = GenUiExportService.exportDocx(tree, "document");
        Assert.assertTrue(pdf.length > 100);
        Assert.assertTrue(docx.length > 100);
        // PDF magic
        Assert.assertEquals('%', (char) pdf[0]);
        Assert.assertEquals('P', (char) pdf[1]);
        Assert.assertEquals('D', (char) pdf[2]);
        Assert.assertEquals('F', (char) pdf[3]);
        // DOCX is zip
        Assert.assertEquals('P', (char) docx[0]);
        Assert.assertEquals('K', (char) docx[1]);
    }
}
