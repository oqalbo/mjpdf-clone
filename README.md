# MJ PDF Reader — Personal Gesture Changes

This is a personal build of MJ PDF Reader 3.1.2 with three extra gesture options and an optional cross-page text selection mode. Each one is a permanent toggle that applies to every PDF and stays set between launches.

## Added options

- **Disable Double Tap Zoom** — turns off zooming by double-tapping the page.
- **Disable Double Tap Drag Zoom** — turns off the double-tap-and-drag quick zoom gesture.
- **Drag After Holding** — lets you hold a finger on the page and then drag to move the page instead of it getting stuck. Holding on selectable text still starts text selection.
- **Select Text Across Pages** — lets you drag a text selection handle onto the next or previous page and keep selecting there. Copying joins the selected pages with a line break. This is in **Settings → Text** and is off by default.

The three gesture toggles are in **Settings → Reading → Scrolling**. They are off by default, so the reader behaves normally until you enable them.

## Fixes

### 3.1.2

- Added optional cross-page text selection. When enabled, dragging either selection handle past a page boundary continues the selection on the adjacent page, including in two-page layouts. The selection highlight and handles are drawn across every selected page, and copying joins the pages in reading order.
- Highlight colours and notes stay available for single-page selections. They are hidden for a cross-page selection because PDF highlights and annotations are stored against one page.

### 3.1.1

- Holding the second tap of a double-tap-drag zoom no longer starts text selection when the page has selectable text underneath. A normal long press still selects text.
