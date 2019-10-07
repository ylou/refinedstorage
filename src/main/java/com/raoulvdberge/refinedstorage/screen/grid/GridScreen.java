package com.raoulvdberge.refinedstorage.screen.grid;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.GridNetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.render.ElementDrawers;
import com.raoulvdberge.refinedstorage.container.GridContainer;
import com.raoulvdberge.refinedstorage.screen.BaseScreen;
import com.raoulvdberge.refinedstorage.screen.IScreenInfoProvider;
import com.raoulvdberge.refinedstorage.screen.grid.sorting.*;
import com.raoulvdberge.refinedstorage.screen.grid.stack.GridStackItem;
import com.raoulvdberge.refinedstorage.screen.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.screen.grid.view.GridViewFluid;
import com.raoulvdberge.refinedstorage.screen.grid.view.GridViewItem;
import com.raoulvdberge.refinedstorage.screen.grid.view.IGridView;
import com.raoulvdberge.refinedstorage.screen.widget.ScrollbarWidget;
import com.raoulvdberge.refinedstorage.screen.widget.SearchWidget;
import com.raoulvdberge.refinedstorage.screen.widget.TabListWidget;
import com.raoulvdberge.refinedstorage.screen.widget.sidebutton.*;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.tile.grid.GridTile;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.tile.grid.portable.TilePortableGrid;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import com.raoulvdberge.refinedstorage.util.TimeUtils;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.client.config.GuiCheckBox;

import java.util.LinkedList;
import java.util.List;

public class GridScreen extends BaseScreen<GridContainer> implements IScreenInfoProvider {
    private IGridView view;

    private SearchWidget searchField;
    private GuiCheckBox oredictPattern;
    private GuiCheckBox processingPattern;

    private ScrollbarWidget scrollbar;

    private IGrid grid;
    private TabListWidget tabs;

    private boolean wasConnected;

    private int slotNumber;

    public GridScreen(GridContainer container, IGrid grid, PlayerInventory inventory, ITextComponent title) {
        super(container, 227, 0, inventory, title);

        this.grid = grid;
        this.view = grid.getGridType() == GridType.FLUID ? new GridViewFluid(this, getDefaultSorter(), getSorters()) : new GridViewItem(this, getDefaultSorter(), getSorters());
        this.wasConnected = this.grid.isActive();
        this.tabs = new TabListWidget(this, new ElementDrawers(this, font), grid::getTabs, grid::getTotalTabPages, grid::getTabPage, grid::getTabSelected, IGrid.TABS_PER_PAGE);
        this.tabs.addListener(new TabListWidget.ITabListListener() {
            @Override
            public void onSelectionChanged(int tab) {
                grid.onTabSelectionChanged(tab);
            }

            @Override
            public void onPageChanged(int page) {
                grid.onTabPageChanged(page);
            }
        });
    }

    @Override
    protected void onPreInit() {
        super.onPreInit();

        this.ySize = getTopHeight() + getBottomHeight() + (getVisibleRows() * 18);
    }

    @Override
    public void onPostInit(int x, int y) {
        container.initSlots();

        this.tabs.init(xSize - 32);

        this.scrollbar = new ScrollbarWidget(this, 174, getTopHeight(), 12, (getVisibleRows() * 18) - 2);

        if (grid instanceof GridNetworkNode || grid instanceof TilePortableGrid) {
            addSideButton(new SideButtonRedstoneMode(this, grid instanceof GridNetworkNode ? GridTile.REDSTONE_MODE : TilePortableGrid.REDSTONE_MODE));
        }

        int sx = x + 80 + 1;
        int sy = y + 6 + 1;

        if (searchField == null) {
            searchField = new SearchWidget(font, sx, sy, 88 - 6);
            searchField.addListener(() -> {
                this.getView().sort(); // Use getter since this view can be replaced.
            });
            searchField.setMode(grid.getSearchBoxMode());
        } else {
            searchField.x = sx;
            searchField.y = sy;
        }

        if (grid.getGridType() != GridType.FLUID && grid.getViewType() != -1) {
            addSideButton(new SideButtonGridViewType(this, grid));
        }

        addSideButton(new SideButtonGridSortingDirection(this, grid));
        addSideButton(new SideButtonGridSortingType(this, grid));
        addSideButton(new SideButtonGridSearchBoxMode(this));
        addSideButton(new SideButtonGridSize(this, () -> grid.getSize(), size -> grid.onSizeChanged(size)));

        if (grid.getGridType() == GridType.PATTERN) {
            processingPattern = addCheckBox(x + 7, y + getTopHeight() + (getVisibleRows() * 18) + 60, I18n.format("misc.refinedstorage.processing"), GridTile.PROCESSING_PATTERN.getValue(), btn -> {
            });

            boolean showOredict = true;
            if (((GridNetworkNode) grid).isProcessingPattern() && ((GridNetworkNode) grid).getType() == IType.FLUIDS) {
                showOredict = false;
            }

            if (showOredict) {
                oredictPattern = addCheckBox(processingPattern.x + processingPattern.getWidth() + 5, y + getTopHeight() + (getVisibleRows() * 18) + 60, I18n.format("misc.refinedstorage:oredict"), GridTile.OREDICT_PATTERN.getValue(), btn -> {
                });
            }

            addSideButton(new SideButtonType(this, GridTile.PROCESSING_TYPE));
        }

        updateScrollbar();
    }

    public IGrid getGrid() {
        return grid;
    }

    public void setView(IGridView view) {
        this.view = view;
    }

    public IGridView getView() {
        return view;
    }

    @Override
    public void tick(int x, int y) {
        if (wasConnected != grid.isActive()) {
            wasConnected = grid.isActive();

            view.sort();
        }

        tabs.update();
    }

    @Override
    public int getTopHeight() {
        return 19;
    }

    @Override
    public int getBottomHeight() {
        if (grid.getGridType() == GridType.CRAFTING) {
            return 156;
        } else if (grid.getGridType() == GridType.PATTERN) {
            return 169;
        } else {
            return 99;
        }
    }

    @Override
    public int getYPlayerInventory() {
        int yp = getTopHeight() + (getVisibleRows() * 18);

        if (grid.getGridType() == GridType.NORMAL || grid.getGridType() == GridType.FLUID) {
            yp += 16;
        } else if (grid.getGridType() == GridType.CRAFTING) {
            yp += 73;
        } else if (grid.getGridType() == GridType.PATTERN) {
            yp += 86;
        }

        return yp;
    }

    @Override
    public int getRows() {
        return Math.max(0, (int) Math.ceil((float) view.getStacks().size() / 9F));
    }

    @Override
    public int getCurrentOffset() {
        return scrollbar.getOffset();
    }

    @Override
    public String getSearchFieldText() {
        return searchField == null ? "" : searchField.getText();
    }

    @Override
    public int getVisibleRows() {
        switch (grid.getSize()) {
            case IGrid.SIZE_STRETCH:
                int screenSpaceAvailable = height - getTopHeight() - getBottomHeight();

                return Math.max(3, Math.min((screenSpaceAvailable / 18) - 3, Integer.MAX_VALUE));//@TODO: MaxRowsStretch
            case IGrid.SIZE_SMALL:
                return 3;
            case IGrid.SIZE_MEDIUM:
                return 5;
            case IGrid.SIZE_LARGE:
                return 8;
            default:
                return 3;
        }
    }

    private boolean isOverSlotWithStack() {
        return grid.isActive() && isOverSlot() && slotNumber < view.getStacks().size();
    }

    private boolean isOverSlot() {
        return slotNumber >= 0;
    }

    public boolean isOverSlotArea(int mouseX, int mouseY) {
        return RenderUtils.inBounds(7, 19, 162, 18 * getVisibleRows(), mouseX, mouseY);
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    private boolean isOverClear(int mouseX, int mouseY) {
        int y = getTopHeight() + (getVisibleRows() * 18) + 4;

        switch (grid.getGridType()) {
            case CRAFTING:
                return RenderUtils.inBounds(82, y, 7, 7, mouseX, mouseY);
            case PATTERN:
                if (((GridNetworkNode) grid).isProcessingPattern()) {
                    return RenderUtils.inBounds(154, y, 7, 7, mouseX, mouseY);
                }

                return RenderUtils.inBounds(82, y, 7, 7, mouseX, mouseY);
            default:
                return false;
        }
    }

    private boolean isOverCreatePattern(int mouseX, int mouseY) {
        return grid.getGridType() == GridType.PATTERN && RenderUtils.inBounds(172, getTopHeight() + (getVisibleRows() * 18) + 22, 16, 16, mouseX, mouseY) && ((GridNetworkNode) grid).canCreatePattern();
    }

    @Override
    public void renderBackground(int x, int y, int mouseX, int mouseY) {
        tabs.drawBackground(x, y - tabs.getHeight());

        if (grid instanceof IPortableGrid) {
            bindTexture(RS.ID, "gui/portable_grid.png");
        } else if (grid.getGridType() == GridType.CRAFTING) {
            bindTexture(RS.ID, "gui/crafting_grid.png");
        } else if (grid.getGridType() == GridType.PATTERN) {
            bindTexture(RS.ID, "gui/pattern_grid" + (((GridNetworkNode) grid).isProcessingPattern() ? "_processing" : "") + ".png");
        } else {
            bindTexture(RS.ID, "gui/grid.png");
        }

        int yy = y;

        blit(x, yy, 0, 0, xSize - 34, getTopHeight());

        // Filters and/or portable grid disk
        blit(x + xSize - 34 + 4, y, 197, 0, 30, grid instanceof IPortableGrid ? 114 : 82);

        int rows = getVisibleRows();

        for (int i = 0; i < rows; ++i) {
            yy += 18;

            blit(x, yy, 0, getTopHeight() + (i > 0 ? (i == rows - 1 ? 18 * 2 : 18) : 0), xSize - 34, 18);
        }

        yy += 18;

        blit(x, yy, 0, getTopHeight() + (18 * 3), xSize - 34, getBottomHeight());

        if (grid.getGridType() == GridType.PATTERN) {
            int ty = 0;

            if (isOverCreatePattern(mouseX - guiLeft, mouseY - guiTop)) {
                ty = 1;
            }

            if (!((GridNetworkNode) grid).canCreatePattern()) {
                ty = 2;
            }

            blit(x + 172, y + getTopHeight() + (getVisibleRows() * 18) + 22, 240, ty * 16, 16, 16);
        }

        tabs.drawForeground(x, y - tabs.getHeight(), mouseX, mouseY, true);

        if (searchField != null) {
            searchField.render(0, 0, 0);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        super.render(mouseX, mouseY, partialTicks);

        // Drawn in here for bug #1844 (https://github.com/raoulvdberge/refinedstorage/issues/1844)
        // Item tooltips can't be rendered in the foreground layer due to the X offset translation.
        if (isOverSlotWithStack()) {
            drawGridTooltip(view.getStacks().get(slotNumber), mouseX, mouseY);
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        renderString(7, 7, title.getFormattedText());
        renderString(7, getYPlayerInventory() - 12, I18n.format("container.inventory"));

        int x = 8;
        int y = 19;

        this.slotNumber = -1;

        int slot = scrollbar != null ? (scrollbar.getOffset() * 9) : 0;

        RenderHelper.enableGUIStandardItemLighting();

        for (int i = 0; i < 9 * getVisibleRows(); ++i) {
            if (RenderUtils.inBounds(x, y, 16, 16, mouseX, mouseY) || !grid.isActive()) {
                this.slotNumber = slot;
            }

            if (slot < view.getStacks().size()) {
                view.getStacks().get(slot).draw(this, x, y);
            }

            if (RenderUtils.inBounds(x, y, 16, 16, mouseX, mouseY) || !grid.isActive()) {
                int color = grid.isActive() ? -2130706433 : 0xFF5B5B5B;

                GlStateManager.disableLighting();
                GlStateManager.disableDepthTest();
                GlStateManager.colorMask(true, true, true, false);
                fillGradient(x, y, x + 16, y + 16, color, color);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
                GlStateManager.enableDepthTest();
            }

            slot++;

            x += 18;

            if ((i + 1) % 9 == 0) {
                x = 8;
                y += 18;
            }
        }

        if (isOverClear(mouseX, mouseY)) {
            renderTooltip(mouseX, mouseY, I18n.format("misc.refinedstorage:clear"));
        }

        if (isOverCreatePattern(mouseX, mouseY)) {
            renderTooltip(mouseX, mouseY, I18n.format("gui.refinedstorage.grid.pattern_create"));
        }

        tabs.drawTooltip(font, mouseX, mouseY);
    }

    private void drawGridTooltip(IGridStack gridStack, int mouseX, int mouseY) {
        List<String> textLines = Lists.newArrayList(gridStack.getTooltip().split("\n"));
        List<String> smallTextLines = Lists.newArrayList();

        if (!gridStack.doesDisplayCraftText()) {
            smallTextLines.add(I18n.format("misc.refinedstorage:total", gridStack.getFormattedFullQuantity()));
        }

        if (gridStack.getTrackerEntry() != null) {
            smallTextLines.add(TimeUtils.getAgo(gridStack.getTrackerEntry().getTime(), gridStack.getTrackerEntry().getName()));
        }

        ItemStack stack = gridStack instanceof GridStackItem ? ((GridStackItem) gridStack).getStack() : ItemStack.EMPTY;

        RenderUtils.drawTooltipWithSmallText(textLines, smallTextLines, RS.INSTANCE.config.detailedTooltip, stack, mouseX, mouseY, xSize, ySize, font);
    }

    /* TODO
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        tabs.actionPerformed(button);

        if (button == oredictPattern) {
            TileDataManager.setParameter(TileGrid.OREDICT_PATTERN, oredictPattern.isChecked());
        } else if (button == processingPattern) {
            // Rebuild the inventory slots before the slot change packet arrives.
            TileGrid.PROCESSING_PATTERN.setValue(false, processingPattern.isChecked());
            ((NetworkNodeGrid) grid).clearMatrix(); // The server does this but let's do it earlier so the client doesn't notice.
            ((ContainerGrid) this.inventorySlots).initSlots();

            TileDataManager.setParameter(TileGrid.PROCESSING_PATTERN, processingPattern.isChecked());
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int clickedButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, clickedButton);

        tabs.mouseClicked();

        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, clickedButton);
        }

        boolean clickedClear = clickedButton == 0 && isOverClear(mouseX - guiLeft, mouseY - guiTop);
        boolean clickedCreatePattern = clickedButton == 0 && isOverCreatePattern(mouseX - guiLeft, mouseY - guiTop);

        if (clickedCreatePattern) {
            BlockPos gridPos = ((NetworkNodeGrid) grid).getPos();

            RS.INSTANCE.network.sendToServer(new MessageGridPatternCreate(gridPos.getX(), gridPos.getY(), gridPos.getZ()));
        } else if (grid.isActive()) {
            if (clickedClear && grid instanceof IGridNetworkAware) {
                RS.INSTANCE.network.sendToServer(new MessageGridClear());
            }

            ItemStack held = ((ContainerGrid) this.inventorySlots).getPlayer().inventory.getItemStack();

            if (isOverSlotArea(mouseX - guiLeft, mouseY - guiTop) && !held.isEmpty() && (clickedButton == 0 || clickedButton == 1)) {
                RS.INSTANCE.network.sendToServer(grid.getGridType() == GridType.FLUID ? new MessageGridFluidInsertHeld() : new MessageGridItemInsertHeld(clickedButton == 1));
            }

            if (isOverSlotWithStack()) {
                boolean isMiddleClickPulling = !held.isEmpty() && clickedButton == 2;
                boolean isPulling = held.isEmpty() || isMiddleClickPulling;

                IGridStack stack = view.getStacks().get(slotNumber);

                if (isPulling) {
                    if (stack.isCraftable() && view.canCraft() && (stack.doesDisplayCraftText() || (GuiScreen.isShiftKeyDown() && GuiScreen.isCtrlKeyDown()))) {
                        FMLCommonHandler.instance().showGuiScreen(new GuiGridCraftingSettings(this, ((ContainerGrid) this.inventorySlots).getPlayer(), stack));
                    } else if (grid.getGridType() == GridType.FLUID && held.isEmpty()) {
                        RS.INSTANCE.network.sendToServer(new MessageGridFluidPull(view.getStacks().get(slotNumber).getHash(), GuiScreen.isShiftKeyDown()));
                    } else if (grid.getGridType() != GridType.FLUID) {
                        int flags = 0;

                        if (clickedButton == 1) {
                            flags |= IItemGridHandler.EXTRACT_HALF;
                        }

                        if (GuiScreen.isShiftKeyDown()) {
                            flags |= IItemGridHandler.EXTRACT_SHIFT;
                        }

                        if (clickedButton == 2) {
                            flags |= IItemGridHandler.EXTRACT_SINGLE;
                        }

                        RS.INSTANCE.network.sendToServer(new MessageGridItemPull(stack.getHash(), flags));
                    }
                }
            }
        }

        if (clickedClear || clickedCreatePattern) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        if (searchField == null) {
            return;
        }

        if (checkHotbarKeys(keyCode)) {
            // NO OP
        } else if (searchField.textboxKeyTyped(character, keyCode)) {
            keyHandled = true;
        } else if (keyCode == RSKeyBindings.CLEAR_GRID_CRAFTING_MATRIX.getKeyCode()) {
            RS.INSTANCE.network.sendToServer(new MessageGridClear());
        } else {
            super.keyTyped(character, keyCode);
        }
    }*/

    public SearchWidget getSearchField() {
        return searchField;
    }

    public void updateOredictPattern(boolean checked) {
        if (oredictPattern != null) {
            oredictPattern.setIsChecked(checked);
        }
    }

    public void updateScrollbar() {
        if (scrollbar != null) {
            scrollbar.setEnabled(getRows() > getVisibleRows());
            scrollbar.setMaxOffset(getRows() - getVisibleRows());
        }
    }

    public static List<IGridSorter> getSorters() {
        List<IGridSorter> sorters = new LinkedList<>();
        sorters.add(getDefaultSorter());
        sorters.add(new GridSorterQuantity());
        sorters.add(new GridSorterID());
        sorters.add(new GridSorterInventoryTweaks());
        sorters.add(new GridSorterLastModified());

        return sorters;
    }

    public static IGridSorter getDefaultSorter() {
        return new GridSorterName();
    }
}