package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyBoxLayoutPackTest {

    @Test
    void editorUsesBuiltInBoxesWhenWorldTagsAreUnavailable() {
        assertEquals(List.of(
            "xero_delta:safety_box_2x1",
            "xero_delta:safety_box_2x2",
            "xero_delta:safety_box_3x2",
            "xero_delta:safety_box_3x3",
            "xero_delta:safety_box_4x2"
        ), SafetyBoxLayoutPack.editorBoxIds(List.of()));
    }

    @Test
    void editorUsesIdsLoadedFromTheLayoutPack() {
        SafetyBoxLayoutPack.LayoutData first = new SafetyBoxLayoutPack.LayoutData();
        first.boxId = "xero_delta:safety_box_3x3";
        SafetyBoxLayoutPack.LayoutData second = new SafetyBoxLayoutPack.LayoutData();
        second.boxId = "example:custom_box";

        assertEquals(List.of("xero_delta:safety_box_3x3", "example:custom_box"),
            SafetyBoxLayoutPack.editorBoxIds(List.of(first, second, first)));
    }
    @Test
    void normalizesIndependentPanelPadding() {
        SafetyBoxLayoutPack.LayoutData layout = new SafetyBoxLayoutPack.LayoutData();
        layout.boxId = "";
        layout.panelPadLeft = 4;
        layout.panelPadRight = 9;
        layout.root = null;

        SafetyBoxLayoutPack.normalizeLayout(layout);

        assertEquals(4, layout.panelPadLeft);
        assertEquals(9, layout.panelPadRight);
        assertNotNull(layout.root);
        assertFalse(layout.containers.isEmpty());
    }

    @Test
    void createsLegacyWidgetTreeWhenMissing() {
        SafetyBoxLayoutPack.LayoutData layout = SafetyBoxLayoutPack.createDefaultLayout("");

        assertEquals(0, layout.borderPad);
        assertEquals(0, layout.panelPadLeft);
        assertEquals(0, layout.panelPadRight);
        assertEquals(0, layout.panelPadTop);
        assertEquals(0, layout.panelPadBottom);
        assertNotNull(layout.root);
        assertEquals("layout", layout.root.type);
        assertEquals(4, layout.root.children.size());
        assertEquals("grid", layout.root.children.get(3).type);
    }

    @Test
    void addsNestedWidgetsAndIndependentGridContainers() {
        SafetyBoxLayoutPack.LayoutData layout = SafetyBoxLayoutPack.createDefaultLayout("");
        int originalContainers = layout.containers.size();

        SafetyBoxLayoutPack.WidgetNode nestedLayout = SafetyBoxLayoutPack.addWidget(layout, layout.root, "layout");
        SafetyBoxLayoutPack.WidgetNode text = SafetyBoxLayoutPack.addWidget(layout, nestedLayout, "text");
        SafetyBoxLayoutPack.WidgetNode firstGrid = SafetyBoxLayoutPack.addWidget(layout, nestedLayout, "grid");
        SafetyBoxLayoutPack.WidgetNode secondGrid = SafetyBoxLayoutPack.addWidget(layout, nestedLayout, "grid");

        assertSame(nestedLayout, SafetyBoxLayoutPack.findParent(layout, text));
        assertEquals(originalContainers + 2, layout.containers.size());
        assertTrue(firstGrid.containerIndex != secondGrid.containerIndex);
        assertTrue(SafetyBoxLayoutPack.removeWidget(layout, text));
        assertNull(SafetyBoxLayoutPack.findWidget(layout, text.id));
        assertTrue(SafetyBoxLayoutPack.removeWidget(layout, firstGrid));
        assertEquals(originalContainers + 1, layout.containers.size());
        assertEquals(originalContainers, secondGrid.containerIndex);
    }

    @Test
    void movesWidgetsBetweenParentsWithoutChangingAbsolutePosition() {
        SafetyBoxLayoutPack.LayoutData layout = SafetyBoxLayoutPack.createDefaultLayout("");
        SafetyBoxLayoutPack.WidgetNode first = SafetyBoxLayoutPack.addWidget(layout, layout.root, "layout");
        first.x = 10;
        first.y = 20;
        SafetyBoxLayoutPack.WidgetNode second = SafetyBoxLayoutPack.addWidget(layout, layout.root, "layout");
        second.x = 100;
        second.y = 40;
        SafetyBoxLayoutPack.WidgetNode text = SafetyBoxLayoutPack.addWidget(layout, first, "text");
        text.x = 7;
        text.y = 9;

        assertEquals(17, SafetyBoxLayoutPack.absoluteX(layout, text));
        assertEquals(29, SafetyBoxLayoutPack.absoluteY(layout, text));
        assertTrue(SafetyBoxLayoutPack.moveWidget(layout, text, second, 0));
        assertSame(second, SafetyBoxLayoutPack.findParent(layout, text));
        assertEquals(17, SafetyBoxLayoutPack.absoluteX(layout, text));
        assertEquals(29, SafetyBoxLayoutPack.absoluteY(layout, text));
        assertFalse(SafetyBoxLayoutPack.moveWidget(layout, second, text, 0));
    }
}
