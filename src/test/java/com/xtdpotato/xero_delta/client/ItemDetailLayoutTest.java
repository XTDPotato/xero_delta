package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDetailLayoutTest {
    @Test
    void descriptiveCardsKeepRoomBelowAllActions() {
        assertEquals(271, ItemDetailLayout.minimumContentHeight(5));
        assertEquals(183, ItemDetailLayout.minimumContentHeight(1));
    }
    @Test
    void automaticHeightReservesEveryActionRow() {
        assertEquals(193, ItemDetailLayout.adaptiveHeight(3));
        assertEquals(149, ItemDetailLayout.adaptiveHeight(1));
    }

    @Test
    void automaticHeightIncludesItemSpecificTopContent() {
        assertEquals(227,
            ItemDetailLayout.resolveHeight(true, 155, 3, 34, 500));
    }

    @Test
    void automaticWidthGrowsWithContent() {
        assertEquals(150,
            ItemDetailLayout.resolveWidth(true, 120, 150, 500));
        assertEquals(248,
            ItemDetailLayout.resolveWidth(true, 120, 248, 500));
    }

    @Test
    void automaticSizeFitsSmallScreens() {
        assertEquals(144,
            ItemDetailLayout.resolveWidth(true, 180, 260, 160));
        assertEquals(104,
            ItemDetailLayout.resolveHeight(true, 155, 3, 120));
    }

    @Test
    void manualSizeCannotShrinkIntoOverlappingGeometry() {
        assertEquals(ItemDetailLayout.MIN_MANUAL_WIDTH,
            ItemDetailLayout.resolveWidth(false, 20, 300, 500));
        assertEquals(ItemDetailLayout.MIN_MANUAL_HEIGHT,
            ItemDetailLayout.resolveHeight(false, 20, 3, 500));
    }

    @Test
    void previewAndMetadataRemainSeparateRegardlessOfActionCount() {
        int previewTop = ItemDetailLayout.HEADER_HEIGHT + 2;
        int itemBottom = previewTop + 20 + ItemDetailLayout.ITEM_CELL_SIZE;
        int actionTop = ItemDetailLayout.HEADER_HEIGHT + ItemDetailLayout.PREVIEW_HEIGHT;
        int matrixTop = actionTop - 4 - 16 - 4;
        assertTrue(itemBottom <= matrixTop);
        for (int rows : new int[] {1, 3, 5, 7}) {
            assertEquals(actionTop + rows * ItemDetailLayout.ACTION_ROW_SPAN + 10,
                ItemDetailLayout.contentHeight(rows, 0, false));
        }
    }

    @Test
    void automaticHeightNeverScalesBelowItsUnscaledControls() {
        for (float scale : new float[] {0.5F, 1.0F, 2.0F}) {
            int content = ItemDetailLayout.contentHeight(5, 100, false);
            assertTrue(ItemDetailLayout.resolveHeight(true, 130, 5, 100, scale, 1000)
                >= content);
        }
        assertEquals(284, ItemDetailLayout.resolveHeight(true, 130, 5, 100, 0.5F, 300));
    }

    @Test
    void shortViewportsScrollToAllContentWithoutCompressingIt() {
        for (int rows : new int[] {1, 3, 5, 7}) {
            for (int extra : new int[] {0, 34, 300}) {
                int content = ItemDetailLayout.contentHeight(rows, extra, false);
                int viewport = ItemDetailLayout.resolveHeight(true, 130, rows, extra, 120);
                double end = ItemDetailLayout.clampScroll(Double.MAX_VALUE, content, viewport);
                assertEquals(content, viewport + end);
                assertEquals(0, ItemDetailLayout.clampScroll(-100, content, viewport));
                assertEquals(0, ItemDetailLayout.clampScroll(100, content, content + 1));
            }
        }
    }

    @Test
    void splitControlsHaveTheirOwnContentExtentAndCanBeReached() {
        int content = ItemDetailLayout.contentHeight(5, 300, true);
        assertEquals(177, content);
        assertTrue(content - 49 >= ItemDetailLayout.HEADER_HEIGHT + ItemDetailLayout.PREVIEW_HEIGHT);
        assertEquals(77, ItemDetailLayout.clampScroll(Double.MAX_VALUE, content, 100));
    }

    @Test
    void scrollingUsesAnIdenticalOffsetForRenderingAndHitTesting() {
        var sections = ItemDetailLayout.sections(200, 4, 0, false, 3);
        int offset = sections.actionsOffset(3, 0);
        int buttonY = ItemDetailLayout.HEADER_HEIGHT + ItemDetailLayout.previewSectionHeight(3)
            + 3 * ItemDetailLayout.ACTION_ROW_SPAN;
        int screenY = buttonY + offset;
        assertTrue(screenY >= sections.actionsTop());
        assertTrue(screenY + ItemDetailLayout.ACTION_HEIGHT <= sections.infoTop());
        assertEquals(buttonY, screenY - offset);
        var small = ItemDetailLayout.sections(100, 7, 0, false, 3);
        assertEquals(small.actionsOffset(3, 0) - 12, small.actionsOffset(3, 12.75));
    }

    @Test
    void eachFootprintCellAlwaysHasTheSameDisplaySize() {
        int unit = ItemDetailLayout.itemExtent(1);
        assertEquals(48, unit);
        for (int columns = 1; columns <= 10; columns++) {
            for (int rows = 1; rows <= 10; rows++) {
                assertEquals(unit * columns, ItemDetailLayout.itemExtent(columns));
                assertEquals(unit * rows, ItemDetailLayout.itemExtent(rows));
                assertEquals(columns / (double) rows, ItemDetailLayout.itemExtent(columns)
                    / (double) ItemDetailLayout.itemExtent(rows));
            }
        }
        // A wide item gains width, but its single row never gets shorter.
        assertEquals(96, ItemDetailLayout.itemExtent(2));
        assertEquals(48, ItemDetailLayout.itemExtent(1));
        assertEquals(144, ItemDetailLayout.itemExtent(3));
    }

    @Test
    void cardHeightTracksItemRowsWithoutSquashingThePreview() {
        for (int rows = 1; rows <= 10; rows++) {
            int extra = (rows - 1) * ItemDetailLayout.ITEM_CELL_SIZE;
            int section = ItemDetailLayout.previewSectionHeight(rows);
            assertEquals(ItemDetailLayout.PREVIEW_HEIGHT + extra, section);
            int itemBottom = ItemDetailLayout.HEADER_HEIGHT + 22 + ItemDetailLayout.itemExtent(rows);
            int actionTop = ItemDetailLayout.HEADER_HEIGHT + section;
            int matrixTop = actionTop - 4 - 16 - 4;
            assertTrue(itemBottom + 8 <= matrixTop);
            assertEquals(ItemDetailLayout.contentHeight(5, 34, false, rows),
                ItemDetailLayout.resolveHeight(true, 130, 5, 34 + extra, 1.0F, 10000));
            for (boolean split : new boolean[] {false, true}) {
                int base = ItemDetailLayout.contentHeight(5, 34, split);
                int content = ItemDetailLayout.contentHeight(5, 34, split, rows);
                assertEquals(base + extra, content);
                int scroll = (int) ItemDetailLayout.clampScroll(Double.MAX_VALUE, content, 160);
                assertEquals(content, 160 + scroll);
            }
        }
    }

    @Test
    void automaticWidthFitsTheFootprintBeforeApplyingScreenLimits() {
        int needed = ItemDetailLayout.itemExtent(6) + 16;
        assertEquals(304, needed);
        for (float scale : new float[] {0.5F, 1.0F, 2.0F}) {
            assertTrue(ItemDetailLayout.resolveWidth(true, 120, needed, scale, 1000) >= needed);
        }
        assertEquals(164, ItemDetailLayout.resolveWidth(true, 120, needed, 0.5F, 180));
        assertEquals(288, ItemDetailLayout.itemExtent(6));
        assertEquals(120, ItemDetailLayout.resolveWidth(false, 120, needed, 1.0F, 1000));
    }

    @Test
    void horizontalPreviewScrollReachesBothEdgesWithoutResizingTheItem() {
        int viewport = 120;
        int content = ItemDetailLayout.itemExtent(10);
        assertEquals(30, ItemDetailLayout.previewThumbWidth(viewport, content));
        assertEquals(0, ItemDetailLayout.previewScrollAt(0, viewport, content));
        assertEquals(180, ItemDetailLayout.previewScrollAt(viewport / 2.0, viewport, content));
        assertEquals(360, ItemDetailLayout.previewScrollAt(viewport, viewport, content));
        assertEquals(360, ItemDetailLayout.previewScrollAt(viewport + 100, viewport, content));
        assertEquals(0, ItemDetailLayout.previewScrollAt(-100, viewport, content));
        assertEquals(0, ItemDetailLayout.previewScrollAt(60, viewport, 48));
        assertEquals(480, content);
    }

    @Test
    void titleHasExactlyTwoPixelsAboveAndBelowItsText() {
        assertEquals(2, ItemDetailLayout.HEADER_PADDING);
        assertEquals(13, ItemDetailLayout.HEADER_HEIGHT);
        assertEquals(ItemDetailLayout.HEADER_TEXT_HEIGHT + 4, ItemDetailLayout.HEADER_HEIGHT);
    }

    @Test
    void fittingCardsShowEveryRegionWithoutAnyScrolling() {
        for (int rows : new int[] {1, 3, 5, 7}) {
            for (int itemRows : new int[] {1, 2, 3, 10}) {
                for (int information : new int[] {0, 34, 300}) {
                    for (boolean split : new boolean[] {false, true}) {
                        int height = ItemDetailLayout.contentHeight(rows, information, split, itemRows);
                        var sections = ItemDetailLayout.sections(height, rows, information, split, itemRows);
                        assertEquals(0, sections.previewMaxScroll());
                        assertEquals(0, sections.actionsMaxScroll());
                        assertEquals(0, sections.infoMaxScroll());
                        assertEquals(height - 1, sections.infoTop() + sections.infoHeight());
                    }
                }
            }
        }
    }

    @Test
    void warehouseActionsStayVisibleWhenOnlyThePreviewOverflows() {
        var sections = ItemDetailLayout.sections(200, 4, 0, false, 2);
        assertTrue(sections.previewMaxScroll() > 0);
        assertEquals(0, sections.actionsMaxScroll());
        assertEquals(0, sections.infoMaxScroll());
        int lastButtonBottom = sections.actionsTop() + ItemDetailLayout.METADATA_HEIGHT
            + 3 * ItemDetailLayout.ACTION_ROW_SPAN + ItemDetailLayout.ACTION_HEIGHT;
        assertTrue(lastButtonBottom <= sections.infoTop());
        assertTrue(sections.infoTop() < 200);
    }

    @Test
    void lengthyInformationScrollsIndependentlyOfTheButtons() {
        var sections = ItemDetailLayout.sections(300, 5, 500, false, 3);
        assertEquals(0, sections.actionsMaxScroll());
        assertTrue(sections.previewMaxScroll() > 0);
        assertTrue(sections.infoMaxScroll() > 0);
        assertTrue(sections.infoHeight() >= 24);
        assertTrue(sections.previewHeight() >= 24);
    }

    @Test
    void verySmallCardsKeepEveryRegionReachableAndWithinTheBorder() {
        for (int height : new int[] {80, 100, 130, 200, 300, 800}) {
            var sections = ItemDetailLayout.sections(height, 7, 500, false, 10);
            assertTrue(sections.previewHeight() > 0);
            assertTrue(sections.previewImageHeight() > 0);
            assertTrue(sections.actionsHeight() > 0);
            assertTrue(sections.infoHeight() > 0);
            assertEquals(height - 1, sections.infoTop() + sections.infoHeight());
            assertEquals(sections.previewContent(), sections.previewHeight() + sections.previewMaxScroll());
            assertEquals(sections.actionsContent(), sections.actionsHeight() + sections.actionsMaxScroll());
            assertEquals(sections.infoContent(), sections.infoHeight() + sections.infoMaxScroll());
        }
    }

    @Test
    void splitControlsRemainPinnedInsteadOfFollowingThePreviewScroll() {
        var sections = ItemDetailLayout.sections(200, 5, 500, true, 3);
        assertTrue(sections.previewMaxScroll() > 0);
        assertEquals(0, sections.actionsMaxScroll());
        assertEquals(0, sections.infoHeight());
        assertEquals(199, sections.infoTop());
    }

    @Test
    void priceAndFavoriteKeepTheirOwnFixedRowAboveTheScrollingImage() {
        var sections = ItemDetailLayout.sections(200, 4, 0, false, 3);
        assertEquals(21, sections.previewFixedHeight());
        assertEquals(sections.previewHeight(), sections.previewFixedHeight() + sections.previewImageHeight());
        int imageContent = sections.previewContent() - sections.previewFixedHeight();
        assertEquals(sections.previewMaxScroll(), imageContent - sections.previewImageHeight());
        assertTrue(sections.previewMaxScroll() > 0);
    }
}
