package me.skaffy.client.water;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.skaffy.RealisticWater;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class WaterSettingsScreen extends Screen {
	private static String lastCategory = "";
	private static boolean openPending;

	private static final int PANEL_X = 8;
	private static final int PANEL_Y = 8;
	private static final int PANEL_WIDTH = 216;
	private static final int WIDGET_WIDTH = 200;
	private static final int ROW_HEIGHT = 24;

	private final @Nullable Screen lastScreen;
	private final String category;
	private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

	private double scrollOffset = 0.0;
	private int listTop = 64;
	private int listBottom = 200;
	private int totalContentHeight = 0;
	private boolean orbitingCamera = false;

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

	public static void tick(Minecraft mc) {
		me.skaffy.client.liquid.LiquidEditorScreen.tick(mc);
		if (openPending) {
			openPending = false;
			mc.setScreen(new WaterSettingsScreen(mc.screen, lastCategory));
		}
	}

	@Override
	public boolean isPauseScreen() {
		// Keep game ticks, wave physics, and Vulkan command submission running live
		return false;
	}

	@Override
	protected void init() {
		this.scrollWidgets.clear();
		int innerX = PANEL_X + 8;
		int headerY = PANEL_Y + 22;

		// Category selector at top of overlay panel
		CycleButton<String> categoryButton = CycleButton.builder(Component::literal, this.category)
			.withValues(WaterSettingsInfo.categories())
			.create(innerX, headerY, WIDGET_WIDTH, 20, Component.translatable("realistic-water.settings.category"),
				(button, value) -> this.minecraft.setScreen(new WaterSettingsScreen(this.lastScreen, value)));
		categoryButton.setTooltip(Tooltip.create(Component.translatable("realistic-water.settings.hint")));
		this.addRenderableWidget(categoryButton);

		this.listTop = headerY + 26;
		int footerHeight = 52;
		this.listBottom = this.height - PANEL_Y - footerHeight;

		// Build dynamic setting widgets
		for (String name : WaterSettingsInfo.names(this.category)) {
			AbstractWidget w = this.widget(WaterSettingsInfo.get(name));
			w.setWidth(WIDGET_WIDTH);
			this.scrollWidgets.add(w);
			this.addRenderableWidget(w);
		}
		this.totalContentHeight = this.scrollWidgets.size() * ROW_HEIGHT;
		this.clampScroll();
		this.layoutScrollWidgets();

		// Compact Footer Buttons
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
			this.minecraft.setScreen(new WaterSettingsScreen(this.lastScreen, this.category));
		}).bounds(innerX, footerY, halfWidth, 20).build();
		reset.setTooltip(Tooltip.create(Component.translatable("realistic-water.settings.resetCategory.tooltip")));
		this.addRenderableWidget(reset);

		Button liquids = Button.builder(Component.literal("Liquids..."),
			button -> this.minecraft.setScreen(new me.skaffy.client.liquid.LiquidEditorScreen(() -> new WaterSettingsScreen(this.lastScreen, this.category))))
			.bounds(innerX + halfWidth + 4, footerY, halfWidth, 20).build();
		liquids.setTooltip(Tooltip.create(Component.literal("Make your own liquids: pick a kind, a colour and a few settings, then take a bucket of it. Also /water liquids.")));
		this.addRenderableWidget(liquids);

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
			.bounds(innerX, footerY + 24, WIDGET_WIDTH, 20).build());
	}

	private void layoutScrollWidgets() {
		int innerX = PANEL_X + 8;
		for (int i = 0; i < this.scrollWidgets.size(); i++) {
			AbstractWidget w = this.scrollWidgets.get(i);
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Hold Right-Click outside the left panel to orbit camera live
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseX > PANEL_X + PANEL_WIDTH && this.minecraft != null) {
			this.orbitingCamera = true;
			this.minecraft.mouseHandler.grabMouse();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.orbitingCamera && this.minecraft != null) {
			this.orbitingCamera = false;
			this.minecraft.mouseHandler.releaseMouse();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (this.orbitingCamera) {
			// Hide UI chrome while orbiting camera so water view is 100% unobstructed
			return;
		}

		int panelBottom = this.height - PANEL_Y;
		// Translucent HUD backdrop + subtle border
		graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, panelBottom, 0x990D1117);
		graphics.renderOutline(PANEL_X, PANEL_Y, PANEL_WIDTH, panelBottom - PANEL_Y, 0x6658A6FF);

		// Title & subtle hint
		graphics.drawString(this.font, this.title, PANEL_X + 8, PANEL_Y + 7, 0xFFFFFF);
		graphics.drawString(this.font, Component.literal("RMB drag outside to look").withStyle(ChatFormatting.DARK_GRAY),
			PANEL_X + PANEL_WIDTH + 8, PANEL_Y + 7, 0xAAAAAA);

		// Scissor-clip scrollable settings list
		graphics.enableScissor(PANEL_X, this.listTop, PANEL_X + PANEL_WIDTH, this.listBottom);
		for (AbstractWidget w : this.scrollWidgets) {
			if (w.visible) {
				w.render(graphics, mouseX, mouseY, partialTick);
			}
		}
		graphics.disableScissor();

		// Render non-scrolled widgets (category selector & footer buttons) + tooltips
		for (var renderable : this.renderables) {
			if (renderable instanceof AbstractWidget w && !this.scrollWidgets.contains(w)) {
				w.render(graphics, mouseX, mouseY, partialTick);
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Intentionally empty: prevents full-screen blur or dark background dimming
	}

	@Override
	public void onClose() {
		if (this.orbitingCamera && this.minecraft != null) {
			this.minecraft.mouseHandler.releaseMouse();
			this.orbitingCamera = false;
		}
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.lastScreen);
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
				yield CycleButton.builder(Component::literal, options.get(current))
					.withValues(options)
					.create(Component.literal(info.label()), (button, picked) -> {
						set(info.name(), String.valueOf(options.indexOf(picked)));
						if ("performanceMode".equals(info.name())) {
							this.minecraft.setScreen(new WaterSettingsScreen(this.lastScreen, this.category));
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
/**
* Pushes updated WaterSettings values directly into your Vulkan UBO / global state
* so the water shader reflects changes on the very next frame in flight.
*/
private static void syncVulkanGlobals() {
// TODO: Replace with your Vulkan global UBO upload / dirty flag call, e.g.:
// VulkanWaterGlobals.updateFromSettings();
// VulkanWaterGlobals.markDirty();
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
out.append(Component.literal("\n/water set " + info.name()).withStyle(ChatFormatting.DARK_GRAY));
out.append(Component.literal("\n" + info.description()).withStyle(ChatFormatting.GRAY));
out.append(Component.literal("\n\nPerformance cost: " + info.cost().label)
.withStyle(style -> style.withColor(TextColor.fromRgb(COST_COLORS.get(info.cost())))));
String note = info.costNote().isEmpty() ? info.cost().meaning : info.costNote();
out.append(Component.literal("\n" + note).withStyle(ChatFormatting.GRAY));
String def = WaterSettings.defaultValue(info.name());
if (info.kind() == WaterSettingsInfo.Kind.CHOICE) {
List options = WaterSettingsInfo.choices(info.name());
int d = Math.clamp(parseInt(def), 0, Math.max(0, options.size() - 1));
def = options.isEmpty() ? def : options.get(d);
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
out = out.replaceAll("0+$", "").replaceAll("\.$", "");
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
