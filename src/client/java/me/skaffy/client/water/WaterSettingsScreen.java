package me.skaffy.client.water;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import me.skaffy.RealisticWater;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

public class WaterSettingsScreen extends Screen {
	private static String lastCategory = "";
	private static String lastSearch = "";
	private static boolean openPending;
	private static final AtomicLong VULKAN_DIRTY_EPOCH = new AtomicLong(1L);

	private static final int PANEL_X = 8;
	private static final int PANEL_Y = 8;
	private static final int PANEL_WIDTH = 220;
	private static final int WIDGET_WIDTH = 200;
	private static final int ROW_HEIGHT = 24;

	private final @Nullable Screen lastScreen;
	private final String category;
	private final List<SettingEntry> scrollEntries = new ArrayList<>();
	private final List<AbstractWidget> staticWidgets = new ArrayList<>();

	private @Nullable EditBox searchBox;
	private @Nullable CycleButton<String> categoryButton;
	private double scrollOffset = 0.0;
	private int listTop = 90;
	private int listBottom = 200;
	private int totalContentHeight = 0;
	private boolean orbitingCamera = false;

	private record SettingEntry(String name, AbstractWidget widget) {}

	public WaterSettingsScreen(@Nullable Screen lastScreen, String category) {
		super(Component.translatable("realistic-water.settings.title"));
		this.lastScreen = lastScreen;
		List<String> categories = WaterSettingsInfo.categories();
		this.category = categories.contains(category) ? category : categories.getFirst();
		lastCategory = this.category;
	}

	public static void openNextTick() {
		openPending = true;
	}

	/**
	 * Monotonically increasing counter bumped whenever any water setting changes.
	 * Poll this in your Vulkan renderer before vkQueueSubmit to upload UBO data only when dirty.
	 */
	public static long vulkanDirtyEpoch() {
		return VULKAN_DIRTY_EPOCH.get();
	}

	public static void tick(Minecraft mc) {
		me.skaffy.client.liquid.LiquidEditorScreen.tick(mc);
		if (openPending) {
			openPending = false;
			mc.gui.setScreen(new WaterSettingsScreen(mc.gui.screen(), lastCategory));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.staticWidgets.clear();
		int innerX = PANEL_X + 8;
		int searchY = PANEL_Y + 22;

		// 1. Live Cross-Category Search Bar
		EditBox search = new EditBox(this.font, innerX, searchY, WIDGET_WIDTH, 18, Component.literal("Search"));
		search.setMaxLength(64);
		search.setValue(lastSearch);
		search.setHint(Component.literal("Search all settings...").withStyle(ChatFormatting.DARK_GRAY));
		search.setResponder(query -> {
			lastSearch = query;
			this.scrollOffset = 0.0;
			this.rebuildSettingList();
		});
		this.searchBox = search;
		this.staticWidgets.add(this.addRenderableWidget(search));

		// 2. Category Selector (disabled while searching across all categories)
		int catY = searchY + 22;
		CycleButton<String> catBtn = CycleButton.builder(Component::literal, this.category)
			.withValues(WaterSettingsInfo.categories())
			.create(innerX, catY, WIDGET_WIDTH, 20, Component.translatable("realistic-water.settings.category"),
				(button, value) -> {
					lastSearch = "";
					this.minecraft.gui.setScreen(new WaterSettingsScreen(this.lastScreen, value));
				});
		catBtn.setTooltip(Tooltip.create(Component.translatable("realistic-water.settings.hint")));
		this.categoryButton = catBtn;
		this.staticWidgets.add(this.addRenderableWidget(catBtn));

		this.listTop = catY + 25;
		int footerHeight = 52;
		this.listBottom = this.height - PANEL_Y - footerHeight;

		this.rebuildSettingList();

		// 3. Compact Footer Buttons
		int footerY = this.listBottom + 6;
		int halfWidth = (WIDGET_WIDTH - 4) / 2;

		Button reset = Button.builder(Component.translatable("realistic-water.settings.resetCategory"), button -> {
			for (String name : WaterSettingsInfo.names(this.category)) {
				try {
					WaterSettings.reset(name);
				} catch (ReflectiveOperationException | NumberFormatException e) {
					RealisticWater.LOGGER.warn("Couldn't reset {}", name, e);
				}
			}
			syncVulkanGlobals();
			this.minecraft.gui.setScreen(new WaterSettingsScreen(this.lastScreen, this.category));
		}).bounds(innerX, footerY, halfWidth, 20).build();
		reset.setTooltip(Tooltip.create(Component.translatable("realistic-water.settings.resetCategory.tooltip")));
		this.staticWidgets.add(this.addRenderableWidget(reset));

		Button liquids = Button.builder(Component.literal("Liquids..."),
			button -> this.minecraft.gui.setScreen(new me.skaffy.client.liquid.LiquidEditorScreen(() -> new WaterSettingsScreen(this.lastScreen, this.category))))
			.bounds(innerX + halfWidth + 4, footerY, halfWidth, 20).build();
		liquids.setTooltip(Tooltip.create(Component.literal("Make your own liquids: pick a kind, a colour and a few settings, then take a bucket of it. Also /water liquids.")));
		this.staticWidgets.add(this.addRenderableWidget(liquids));

		this.staticWidgets.add(this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
			.bounds(innerX, footerY + 24, WIDGET_WIDTH, 20).build()));
	}

	private void rebuildSettingList() {
		for (SettingEntry entry : this.scrollEntries) {
			this.removeWidget(entry.widget());
		}
		this.scrollEntries.clear();

		String query = lastSearch.trim().toLowerCase(Locale.ROOT);
		boolean searching = !query.isEmpty();
		if (this.categoryButton != null) {
			this.categoryButton.active = !searching;
		}

		List<String> targetNames = new ArrayList<>();
		if (searching) {
			for (String cat : WaterSettingsInfo.categories()) {
				for (String name : WaterSettingsInfo.names(cat)) {
					WaterSettingsInfo.Info info = WaterSettingsInfo.get(name);
					if (name.toLowerCase(Locale.ROOT).contains(query)
						|| info.label().toLowerCase(Locale.ROOT).contains(query)
						|| info.description().toLowerCase(Locale.ROOT).contains(query)) {
						targetNames.add(name);
					}
				}
			}
		} else {
			targetNames.addAll(WaterSettingsInfo.names(this.category));
		}

		for (String name : targetNames) {
			AbstractWidget w = this.widget(WaterSettingsInfo.get(name));
			w.setWidth(WIDGET_WIDTH);
			this.addRenderableWidget(w);
			this.scrollEntries.add(new SettingEntry(name, w));
		}

		this.totalContentHeight = this.scrollEntries.size() * ROW_HEIGHT;
		this.clampScroll();
		this.layoutScrollWidgets();
	}

	private void layoutScrollWidgets() {
		int innerX = PANEL_X + 8;
		for (int i = 0; i < this.scrollEntries.size(); i++) {
			AbstractWidget w = this.scrollEntries.get(i).widget();
			int y = this.listTop + (i * ROW_HEIGHT) - (int) this.scrollOffset;
			w.setX(innerX);
			w.setY(y);
			w.visible = (y + w.getHeight() > this.listTop) && (y < this.listBottom);
		}
	}

	private void clampScroll() {
		int viewHeight = Math.max(1, this.listBottom - this.listTop);
		double maxScroll = Math.max(0.0, this.totalContentHeight - viewHeight);
		this.scrollOffset = Math.clamp(this.scrollOffset, 0.0, maxScroll);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= PANEL_X && mouseX <= PANEL_X + PANEL_WIDTH && mouseY >= this.listTop && mouseY <= this.listBottom) {
			this.scrollOffset -= scrollY * 16.0;
			this.clampScroll();
			this.layoutScrollWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Right-Click outside the left panel -> temporarily grab mouse to orbit camera live
		if (event.button() == 1 && event.x() > PANEL_X + PANEL_WIDTH && this.minecraft != null) {
			this.orbitingCamera = true;
			this.minecraft.mouseHandler.grabMouse();
			return true;
		}

		// Middle-Click on any setting row -> reset that single setting to default
		if (event.button() == 2
			&& event.x() >= PANEL_X && event.x() <= PANEL_X + PANEL_WIDTH
			&& event.y() >= this.listTop && event.y() <= this.listBottom) {
			for (SettingEntry entry : this.scrollEntries) {
				AbstractWidget w = entry.widget();
				if (w.visible && w.active && w.isMouseOver(event.x(), event.y())) {
					try {
						WaterSettings.reset(entry.name());
						syncVulkanGlobals();
						this.rebuildSettingList();
					} catch (ReflectiveOperationException | NumberFormatException e) {
						RealisticWater.LOGGER.warn("Couldn't reset {}", entry.name(), e);
					}
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 1 && this.orbitingCamera && this.minecraft != null) {
			this.orbitingCamera = false;
			this.minecraft.mouseHandler.releaseMouse();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (this.orbitingCamera) {
			return;
		}

		int panelBottom = this.height - PANEL_Y;
		boolean hoveringPanel = mouseX >= PANEL_X && mouseX <= PANEL_X + PANEL_WIDTH
&& mouseY >= PANEL_Y && mouseY <= panelBottom;
// Hover-reactive opacity: subtle when inspecting water, solid when editing controls
int bgColor = hoveringPanel ? 0xCC0D1117 : 0x550D1117;
int borderColor = hoveringPanel ? 0x9958A6FF : 0x4458A6FF;
graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, panelBottom, bgColor);
graphics.outline(PANEL_X, PANEL_Y, PANEL_WIDTH, panelBottom - PANEL_Y, borderColor);
graphics.text(this.font, this.title, PANEL_X + 8, PANEL_Y + 7, 0xFFFFFFFF);
graphics.text(this.font, Component.literal("RMB drag to look • MMB widget to reset").withStyle(ChatFormatting.GRAY),
PANEL_X + PANEL_WIDTH + 8, PANEL_Y + 7, 0xCCDDDDDD);
graphics.enableScissor(PANEL_X, this.listTop, PANEL_X + PANEL_WIDTH, this.listBottom);
for (SettingEntry entry : this.scrollEntries) {
AbstractWidget w = entry.widget();
if (w.visible) {
w.extractRenderState(graphics, mouseX, mouseY, partialTick);
}
}
graphics.disableScissor();
// Slim scrollbar thumb on right edge of panel list
int viewHeight = Math.max(1, this.listBottom - this.listTop);
if (this.totalContentHeight > viewHeight) {
int trackX = PANEL_X + PANEL_WIDTH - 5;
int thumbHeight = Math.max(16, (int) ((double) viewHeight * viewHeight / this.totalContentHeight));
double maxScroll = Math.max(1.0, this.totalContentHeight - viewHeight);
int thumbY = this.listTop + (int) ((this.scrollOffset / maxScroll) * (viewHeight - thumbHeight));
graphics.fill(trackX, this.listTop, trackX + 2, this.listBottom, 0x33FFFFFF);
graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xAA58A6FF);
}
for (AbstractWidget w : this.staticWidgets) {
w.extractRenderState(graphics, mouseX, mouseY, partialTick);
}
}
@Override
public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
// Intentionally empty: keeps live water background completely unobstructed
}
@Override
public void onClose() {
if (this.orbitingCamera && this.minecraft != null) {
this.minecraft.mouseHandler.releaseMouse();
this.orbitingCamera = false;
}
if (this.minecraft != null) {
this.minecraft.gui.setScreen(this.lastScreen);
}
}
@Override
public void removed() {
super.removed();
WaterSettings.save();
}
private AbstractWidget widget(WaterSettingsInfo.Info info) {
AbstractWidget widget = switch (info.kind()) {
case SWITCH -> CycleButton.onOffBuilder(Boolean.parseBoolean(value(info.name())))
.create(Component.literal(info.label()), (button, on) -> set(info.name(), on.toString()));
case CHOICE -> {
				List<String> options = WaterSettingsInfo.choices(info.name());
				int current = Math.clamp(parseInt(value(info.name())), 0, Math.max(0, options.size() - 1));
				yield CycleButton.<String>builder(Component::literal, String.valueOf(options.get(current)))
					.withValues(options)
.create(Component.literal(info.label()), (button, picked) -> {
set(info.name(), String.valueOf(options.indexOf(picked)));
if ("performanceMode".equals(info.name())) {
this.minecraft.gui.setScreen(new WaterSettingsScreen(this.lastScreen, this.category));
}
});
}
case TEXT -> {
EditBox box = new EditBox(this.font, WIDGET_WIDTH, 20, Component.literal(info.label()));
box.setMaxLength(1024);
box.setValue(value(info.name()));
box.setHint(Component.literal(info.label()).withStyle(ChatFormatting.DARK_GRAY));
box.setResponder(text -> set(info.name(), text.isEmpty() ? "-" : text));
yield box;
}
default -> new SettingSlider(info);
};
widget.setTooltip(Tooltip.create(tooltip(info)));
if (PerformanceMode.overrides(info.name())) {
widget.active = false;
}
return widget;
}
private static int parseInt(String value) {
try {
return Integer.parseInt(value);
} catch (NumberFormatException e) {
return 0;
}
}
private static String value(String name) {
try {
return WaterSettings.get(name);
} catch (ReflectiveOperationException e) {
return "";
}
}
private static void set(String name, String value) {
try {
WaterSettings.set(name, value);
syncVulkanGlobals();
} catch (ReflectiveOperationException | NumberFormatException e) {
RealisticWater.LOGGER.warn("Couldn't set {} to {}", name, value, e);
}
}
private static void syncVulkanGlobals() {
VULKAN_DIRTY_EPOCH.incrementAndGet();
// If you have a dedicated Vulkan uniform buffer manager, call its markDirty() here:
// e.g. VulkanWaterRenderer.markGlobalsDirty();
}
private static final Map<WaterSettingsInfo.Cost, Integer> COST_COLORS = Map.of(
WaterSettingsInfo.Cost.NONE, 0x9AA7B8,
WaterSettingsInfo.Cost.LOW, 0x7DFFA0,
WaterSettingsInfo.Cost.MEDIUM, 0xFFE070,
WaterSettingsInfo.Cost.HIGH, 0xFFA050,
WaterSettingsInfo.Cost.EXTREME, 0xFF6060);
public static Component tooltip(WaterSettingsInfo.Info info) {
MutableComponent out = Component.empty();
out.append(Component.literal(info.label()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
out.append(Component.literal("\n/water set " + info.name() + "  (Middle-Click to reset)").withStyle(ChatFormatting.DARK_GRAY));
out.append(Component.literal("\n" + info.description()).withStyle(ChatFormatting.GRAY));
out.append(Component.literal("\n\nPerformance cost: " + info.cost().label)
.withStyle(style -> style.withColor(TextColor.fromRgb(COST_COLORS.get(info.cost())))));
String note = info.costNote().isEmpty() ? info.cost().meaning : info.costNote();
out.append(Component.literal("\n" + note).withStyle(ChatFormatting.GRAY));
String def = WaterSettings.defaultValue(info.name());
if (info.kind() == WaterSettingsInfo.Kind.CHOICE) {
List options = WaterSettingsInfo.choices(info.name());
int d = Math.clamp(parseInt(def), 0, Math.max(0, options.size() - 1));
def = options.isEmpty() ? def : String.valueOf(options.get(d));
}
out.append(Component.literal("\nDefault: " + (def.isEmpty() ? "(empty)" : def)).withStyle(ChatFormatting.DARK_GRAY));
if (PerformanceMode.overrides(info.name())) {
out.append(Component.literal("\n\nLocked by Performance mode (" + PerformanceMode.label(PerformanceMode.mode()) + "). Your value "
+ PerformanceMode.userValue(info.name()) + " comes back when Performance mode is Off.").withStyle(ChatFormatting.YELLOW));
}
return out;
}
public static String format(WaterSettingsInfo.Info info, double value) {
if (info.kind() == WaterSettingsInfo.Kind.INTEGER) {
return String.valueOf(Math.round(value));
}
int decimals = info.step() > 0 ? Math.clamp((int) Math.ceil(-Math.log10(info.step()) - 1e-9), 0, 6) : 3;
String out = String.format(Locale.ROOT, "%." + decimals + "f", value);
if (out.contains(".")) {
out = out.replaceAll("0+$", "").replaceAll("[.]$", "");
}
return out;
}
private static final class SettingSlider extends AbstractSliderButton {
private final WaterSettingsInfo.Info info;
SettingSlider(WaterSettingsInfo.Info info) {
super(0, 0, WIDGET_WIDTH, 20, Component.empty(), position(info));
this.info = info;
this.updateMessage();
}
private static double position(WaterSettingsInfo.Info info) {
double span = info.max() - info.min();
if (span <= 0.0) {
return 0.0;
}
try {
return Math.clamp((Double.parseDouble(value(info.name())) - info.min()) / span, 0.0, 1.0);
} catch (NumberFormatException e) {
return 0.0;
}
}
private double picked() {
double v = this.info.min() + this.value * (this.info.max() - this.info.min());
if (this.info.step() > 0.0) {
v = this.info.min() + Math.round((v - this.info.min()) / this.info.step()) * this.info.step();
}
return Math.clamp(v, this.info.min(), this.info.max());
}
@Override
protected void updateMessage() {
this.setMessage(Component.literal(this.info.label() + ": " + value(this.info.name())));
}
@Override
protected void applyValue() {
set(this.info.name(), format(this.info, this.picked()));
}
}
}
