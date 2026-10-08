# .sfy: GUIs for Skaffy's API

A `.sfy` file is a GUI written in a small Java-like language. The server sends the file to players as it is; their client compiles it and runs it, like a browser runs a website. The file does everything that happens on the player's screen (layout, looks, animations, typing, scrolling). The server (your plugin) does everything that must be trusted (money, items, permissions) and talks to the file with messages and method calls.

Two website rules apply here too:

- Players can read your files (like "view source"), so nothing secret goes in them.
- A modified client can send anything, so the server checks every message: in a shop, the server looks the price up itself and never trusts a price from the file.

The wire format is in PROTOCOL.md (`skaffy:gui`). The test GUIs in `test-plugin/src/main/resources/guis/skaffytest/` are complete examples.

---

## A first GUI

`guis/shop/Shop.sfy` in your plugin's jar:

```java
package shop;

import skaffy.gui.*;

public class Shop {
    int coins;
    Text coinText;

    @Gui
    public void main(int startCoins) {
        coins = startCoins;
        blur(3);
        tint(#80000000);

        Box panel = add(box().pos(50% - 100, 40).size(200, 120).color(#F0181A1F).radius(8).padding(10).column(6));
        coinText = panel.add(text("Coins: " + coins));
        Button buy = panel.add(button("Buy a sword (50)"));
        buy.onClick(() -> send("buy", "sword"));
    }

    public void setCoins(int value) {
        coins = value;
        coinText.text("Coins: " + coins);
    }
}
```

The plugin:

```java
SkaffyGuis guis = SkaffyAPI.get().getGuis();
guis.addFiles(this, "guis");                           // every .sfy in the jar's guis/ folder: shop/Shop.sfy is shop.Shop

guis.onMessage("shop.Shop", "buy", (player, message) -> {
    int balance = economy.balance(player);             // the server decides, not the file
    if (balance >= 50) {
        economy.take(player, 50);
        guis.run(player, "shop.Shop", "setCoins", balance - 50);
    }
});

guis.open(player, "shop.Shop", "main", economy.balance(player));
```

---

## The file

```java
package shop;               // must match the class name the server uses: shop.Shop

import skaffy.gui.*;        // every GUI file imports the GUI library

public class Shop {         // one class per file, named like the file
    int coins;              // instance fields: a fresh set every time the server opens the GUI
    static int opened;      // static fields: kept until the file is sent again or the player leaves
    final int max = 10;     // final: set once (here or in the constructor)

    Shop() { opened++; }    // an optional constructor without parameters, run when the GUI opens

    @Gui                    // a screen the server can open (it must be public)
    public void main() { }

    public int total() { return coins; }   // public: the server can call it
    void helper() { }                      // not public: only the file itself can call it

    record Offer(String name, int price) {}      // records and enums go inside the class
    enum Tab { SHOP, SELL }
}
```

- **One class per file**, named like the file, with a `package` that matches (`shop/Shop.sfy` → `package shop;` → the server opens `shop.Shop`). Files can't import each other.
- **Imports** come right after the package. A GUI file needs `import skaffy.gui.*;` (everything) or single names like `import skaffy.gui.Box;` and `import skaffy.gui.box;` (only those). The GUI library is everything this page lists for GUIs: element types (`Box`, `Text`, ...), their functions (`box()`, `text()`, `add()`, `blur()`, ...), the enums (`Align`, `Easing`, ...), `@Gui` and `@Hud`. The Java-like basics (`String`, `List`, `Map`, `Math`, `Integer`, `Double`, `Color`, `Length`, `Duration`, the lambda types, `LOGGER`) never need an import, and neither do methods called on a value (`box().color(...)`). A file that imports `skaffy.shader` is a shader file (SHADERS.md) and can't be opened as a GUI.
- **`@Gui` methods** are the screens. They're `public`, return `void` and can take parameters (the server passes them). Calling one from your code switches to that screen: everything on screen is removed first, fields stay. See [Screens](#screens). **`@Hud` methods** are the same for HUD layers drawn over the game, see [HUD layers](#hud-layers).
- **`public`** means the server may open or call it. Everything else can only be used from inside the file.
- **Records** can have methods, static fields and one constructor: the main one, or a compact one (`Offer { if (price < 0) price = 0; }`). **Enums** can have fields, one constructor and methods: `enum Size { SMALL(1), BIG(10); final int weight; Size(int w) { weight = w; } }`.
- Records and enums can use the class's static fields and methods and every GUI function, but not its instance fields (like Java's static nested classes).

---

## The language

It's Java with fewer pieces. What's different:

- **Types:** `int`, `long`, `double`, `boolean`, `char`, `String`, `List<T>`, `Map<K, V>`, your records and enums, and the functional types lambdas become: `Runnable`, `Consumer<T>`, `BiConsumer<A, B>`, `Supplier<T>`, `Function<T, R>`, `Predicate<T>`, `Comparator<T>`. `Integer`, `Long`, `Double`, `Boolean` and `Character` exist for type arguments (`List<Integer>`) and values that can be null. Plus the GUI types (`Box`, `Text`, ...) and `Color`, `Length` and `Duration`.
- **No** `float`, `byte`, `short`, arrays (use `List`), inheritance, interfaces, your own generic types, method references (`::`), anonymous classes, labels.
- **Literals** besides Java's:
  - `#RRGGBB` and `#AARRGGBB` are colors (alpha first, like Minecraft): `#FF8800`, `#80000000`.
  - `50%` is a length: half of the parent's inside. `50% - 20` and `(100% - 10) / 2` are lengths too; plain numbers are pixels.
  - `150ms` and `2s` (also `1.5s`) are durations. A plain int is milliseconds.
  - Because of `50%`, the remainder operator needs spaces: `a % b`. `7%3` is a percent followed by `3`, which is a mistake.
  - Text blocks (`"""`) work like Java's.
- **`==` on strings compares their text** (Java compares objects, a classic bug). Numbers, chars, booleans, colors, lengths and durations compare by value too. Records, lists and maps compare by identity; use `equals` for their contents.
- **`var`** works for local variables with a value: `var list = new ArrayList<String>();`.
- **Control flow** is all Java's: `if/else`, `for`, for-each (over a `List`), `while`, `do/while`, `switch` statements and expressions (both `case X ->` and `case X:` with `yield`), `break`, `continue`, `return`, `? :`, `try/catch/finally`, `throw`.
- **Exceptions:** there's one type, `Exception`. `throw new Exception("message")`, `catch (Exception e)` and `e.getMessage()`. Mistakes while running (null, index out of range, `/ by zero`, `Integer.parseInt("abc")`) are Exceptions too and can be caught.
- **Lambdas** work like Java's: `x -> x * 2`, `(a, b) -> { ... }`, `() -> close()`. They can use the variables around them if those never change after being set (Java's "effectively final" rule). Loop variables of a for-each count as new each time, so `for (String name : names) row.onClick(() -> send("pick", name));` works.
- **Bare enum names** work where that enum is expected: `align(RIGHT)` instead of `align(TextAlign.RIGHT)`. Elsewhere, like in `List.of(...)`, write the full name: `List.of(Easing.LINEAR, Easing.EASE_OUT_QUAD)`.
- **Lists and maps can always be changed**, also the ones from `List.of` and `Map.of`. Maps keep the order keys were added in.
- **No limits on running time:** a `while (true)` without a `break` freezes the player's game, just like in Java.

### Printing

```java
LOGGER.info("Opened with {} coins", coins);      // {} is replaced by the next value, like SLF4J
LOGGER.warn("Almost out of space");
LOGGER.error("Something went wrong: {}", e.getMessage());
```

Logs go to the player's game log and to the server's console (at most 40 per second).

### Mistakes

When a file doesn't compile, every mistake is reported with its place, and the file can't be opened:

```
Shop.sfy:14:22: Expected int but this is String (use Integer.parseInt or Double.parseDouble)
Shop.sfy:18:9: Box has no method colour (did you mean color?)
```

Mistakes while running are reported with a trace, and only the code that failed stops (the GUI stays open):

```
Index 3 out of bounds for length 0
    at Shop.pick(Shop.sfy:40)
    at Shop.main (lambda)(Shop.sfy)
```

Both go to the player's game log and the server console (`SkaffyGuiErrorEvent` lets a plugin handle them itself).

### Standard library

Kept small on purpose. If something is missing, it can be added.

| Type | What there is |
|---|---|
| Any value | `equals(Object)`, `hashCode()`, `toString()` |
| `String` | `length()`, `charAt(int)`, `substring(start)`, `substring(start, end)`, `indexOf(String/char)`, `contains`, `startsWith`, `endsWith`, `toLowerCase()`, `toUpperCase()`, `trim()`, `replace(a, b)`, `split(separator)` (plain text, not a regex; gives a `List<String>`), `isEmpty()`, `repeat(n)`, `compareTo`, `String.format(format, values...)`, `String.valueOf(value)` |
| `Math` | `abs`, `min`, `max`, `floor`, `ceil`, `round` (gives a `long`), `sqrt`, `pow`, `sin`, `cos`, `atan2`, `random()`, `clamp(value, min, max)`, `lerp(from, to, t)`, `Math.PI` |
| Numbers | `Integer.parseInt(text)`, `Double.parseDouble(text)` |
| `List<T>` | `List.of(values...)`, `new ArrayList<>()`, `new ArrayList<>(copyOf)`, `add(value)`, `add(index, value)`, `get`, `set`, `remove(index)`, `remove(value)`, `size`, `isEmpty`, `contains`, `indexOf`, `clear`, `sort()`, `sort((a, b) -> ...)` |
| `Map<K, V>` | `Map.of(key, value, ...)`, `new HashMap<>()`, `put`, `get`, `getOrDefault`, `containsKey`, `remove`, `size`, `keys()`, `values()` (both lists) |
| `Color` | `Color.rgb(r, g, b)`, `Color.argb(a, r, g, b)`, `Color.hsb(hue, saturation, brightness)`, `red()`, `green()`, `blue()`, `alpha()`, `withAlpha(a)`, `mix(other, t)` |
| `Length` | `Length.percent(p)`, `Length.px(p)`, and `+ - * /` with numbers |
| `Duration` | `millis()`, and `+ -` with durations |
| Functional types | `run()`, `accept(x)`, `get()`, `apply(x)`, `test(x)`, `compare(a, b)` |

---

## Screens

- The server opens a `public @Gui` method with `guis.open(player, "shop.Shop", "main", args...)`. That always starts fresh: a new instance, so instance fields start over. Static fields keep their values while the player stays (until the file is sent again).
- Calling another `@Gui` method from your code switches screens: every element is removed, and the screen settings (blur, tint, ...), timers and handlers (`onFrame`, `onKey`, `onEscape`, ...) are reset. Fields stay, so screens of one class share them like pages of one website.
- **Esc** closes the GUI, unless `onEscape(() -> ...)` is set; then Esc runs that instead. Pressing Esc three times within a second always closes, so no GUI can trap a player. When a text field is focused, Esc first just leaves the field.
- `close()` closes the GUI from code. The server can close it too, and it closes when another screen replaces it (a chest, the death screen, another GUI).
- Only one GUI is open at a time. The world keeps running behind it; the player can't walk while it's open.

---

## HUD layers

```java
@Hud
public void main() {
    add(canvas(g -> g.ring(6, 6, 3, 1, #FFFFFFFF)).size(12, 12).pos(50% - 6, 50% - 6).blend(INVERT));   // a crosshair
    add(text("").fontSize(8).pos(4, 4));
}
```

- **`@Hud` methods** are HUD layers: the server shows one with `guis.showHud(player, "game.Hud", "main", args...)` (or `showHud(player, order, ...)`; lower order is drawn first). A layer is drawn over the game and over vanilla's HUD, without a screen: the player keeps playing, and the layer never gets the mouse or keys (use server keybinds for keys). Everything else works like in a screen: elements, looks, animations, `onFrame`, timers, `send(...)`, and the server's `call`/`run`. Calling another `@Hud` method from code switches the layer like `@Gui` methods switch screens.
- One layer per class (showing it again replaces it, fresh). It stays through death, respawn and changing worlds and ends when the player leaves or the server hides it (`guis.hideHud(player, "game.Hud")`); `close()` hides it from code. F1 hides layers with vanilla's HUD.
- **Vanilla's HUD parts** can be hidden, moved or scaled by the server, so a layer can draw its own version: `guis.setHudPart(player, HudPart.HOTBAR, false)` or `setHudPart(player, part, visible, x, y, scale)`. Parts: `CROSSHAIR`, `HOTBAR`, `HEALTH`, `ARMOR`, `FOOD`, `AIR`, `EXPERIENCE`, `MOUNT_HEALTH`, `HELD_ITEM_NAME`, `EFFECTS`, `BOSS_BARS`, `SCOREBOARD`, `ACTION_BAR`, `TITLE`, `CHAT`, `PLAYER_LIST`, `SUBTITLES`, `VIGNETTE`, `HELMET_OVERLAYS`, `SLEEP_FADE`.
- **What a file can read about the player** (layers and screens), to draw its own HUD:

| Function | Gives |
|---|---|
| `health()`, `maxHealth()`, `absorption()` | Hit points (`double`; 20 is ten hearts) |
| `armor()`, `food()`, `saturation()` | Armor points (0-20), food (0-20), saturation |
| `air()`, `maxAir()` | Air left under water, in ticks |
| `xpLevel()`, `xpProgress()` | The level, and how far to the next (0 to 1) |
| `attackStrength()` | How charged the next hit is (0 to 1) |
| `selectedSlot()` | The selected hotbar slot (0-8) |
| `slotItem(n)` | An `Item` element that always shows the player's own item in inventory slot n (0-8 hotbar, 9-35 inventory, 36-39 armor, 40 offhand), with its count and durability |
| `bossBars()` | `List<BossBar>`: `name()` (rich text), `progress()` (0 to 1), `color()` (`pink`, `blue`, `red`, `green`, `yellow`, `purple`, `white`), `style()` (`progress`, `notched_6`, `notched_10`, `notched_12`, `notched_20`) |
| `sidebar()` | The scoreboard sidebar, or null: `title()` and `lines()`, a `List<SidebarLine>` with `name()` and `score()` (rich text), highest score first, at most 15 |
| `effects()` | `List<Effect>`: `id()` (`minecraft:speed`), `icon()` (a GUI sprite for `sprite(...)`), `level()` (1 and up), `duration()` (ticks left, -1 forever), `ambient()` |

- **`blend(INVERT)`** draws an element (and its children) like vanilla's crosshair: white turns what's behind it into its opposite color, so it shows on every background. Boxes, borders, canvas drawings and images invert; text doesn't. `blend(NORMAL)` goes back.

---

## Layout

Coordinates are **GUI pixels**, the same pixels vanilla's menus use: they follow the player's GUI scale, like browser zoom. `guiScale(3)` makes a GUI use its own scale instead.

- Every element is placed inside its parent's **inside**: its box minus its padding. Top-level elements (added with `add(...)`) are placed on the screen.
- **Lengths** are `%` of the parent's inside plus pixels: `pos(50% - 100, 20)` puts an element's left edge 100 pixels left of the middle. Widths and heights work the same: `size(100% - 20, 40)`.
- **Without a width or height, an element fits its content**: text its text, an image its picture, a box its children (plus padding). A parent that fits its content can't give its children a size, so `%` inside it only counts the pixel part.
- `padding(all)`, `padding(vertical, horizontal)`, `padding(top, right, bottom, left)`.

### Rows and columns

```java
Box list = box().column(4);                       // children one below the other, 4 pixels apart
Box bar = box().row(8).alignItems(CENTER).justify(BETWEEN).width(100%);
Box grid = box().row(6).wrap(true).width(300);    // continues on the next line when full
```

- `row(gap)` / `column(gap)` place children one after another; `gap(pixels)` changes the space.
- `wrap(true)` starts a new line when the next child doesn't fit.
- `justify(...)` spreads children along the row or column: `START`, `CENTER`, `END`, `BETWEEN`, `AROUND`, `EVENLY`.
- `alignItems(...)` places them across: `START`, `CENTER`, `END`, `STRETCH` (as big as the line).
- In a row or column, `x`/`y` still move a child from its place. `absolute(true)` takes a child out of the flow; it's placed by its `pos` like in a plain box.

### Layers, scrolling, clipping

- `z(n)`: higher is drawn on top among siblings; children are always on top of their parent.
- **The page scrolls** like a website when elements reach past the bottom or right of the screen: the wheel scrolls down, **Cmd + wheel (Mac) or Alt + wheel** scrolls sideways. There's a scrollbar; `pageScrollbar(style)` changes it. Only content past the right and bottom counts.
- `fixed(true)` keeps an element in place when its parent scrolls (headers, close buttons).
- `scroll(true)` makes a box its own scroll area: children are cut off at its edge and the wheel scrolls it first. `scrollbar(VANILLA | MODERN | CUSTOM | HIDDEN)`, and for custom ones `scrollbarColors(thumb, track)` and `scrollbarWidth(pixels)`. `scrollTo(x, y)`, `scrollX()`, `scrollY()`.
- `clip(true)` cuts children off at the box's edge without scrolling.

### Transforms

`offset(x, y)`, `rotate(degrees)`, `scale(factor)` and `origin(x, y)` (the point they turn and grow around, the middle by default) move what's drawn without changing the layout, like CSS transforms. Clicks follow the transformed shape.

### Reading positions

`x()`, `y()`, `width()` and `height()` give the laid-out pixels (in the parent's inside, before transforms and scrolling), as of the last frame. `screenX()` and `screenY()` give the top-left corner on screen.

---

## Looks

Every element has a box behind it: fill, border, rounded corners and shadow.

| Method | What it does |
|---|---|
| `color(color)` | The element's main color: a box's or button's fill, a text's color, an image's or head's tint |
| `background(color)` | The fill behind elements whose `color` is something else (text, images, ...) |
| `textColor(color)` | A button's or field's text |
| `gradient(colors...)`, `gradient(angle, colors...)` | A gradient fill instead of a color: 2 to 16 colors, the angle in degrees like CSS (180, the default, goes top to bottom) |
| `radius(r)`, `radius(topLeft, topRight, bottomRight, bottomLeft)` | Rounded corners |
| `pixelCorners(true)` | Rounded corners as pixel steps (the Minecraft look) instead of smooth |
| `border(width, color)`, `borderColor(color)` | A border inside the edge. The width is in GUI pixels and can be a fraction: `border(1.0 / guiScale(), color)` is one screen pixel at every GUI scale |
| `shadow(color, blur)`, `shadow(color, blur, x, y)`, `shadow(color, blur, x, y, spread)` | A soft shadow or glow |
| `transparency(0 to 100)` | 0 is solid, 100 invisible; children fade with their parent |
| `fontSize(pixels)` | Text size (8 is vanilla's) of texts, buttons and fields |

---

## States, transitions and animations

```java
Box row = box().color(#FF24262D).radius(4);
row.hover().color(#FF30333C).scale(1.02);    // while the mouse is over it (or its children)
row.pressed().color(#FF1C1E24);              // while it's held down
row.focused().borderColor(#FFFFFFFF);        // while it has keyboard focus
row.disabledStyle().transparency(50);        // while disabled(true)
row.transition(120ms, EASE_OUT_QUAD);        // every change blends over 120 ms
```

- States go back by themselves when they end, like CSS's `:hover`. Pressed wins over focused, which wins over hover; disabled wins over everything.
- `transition(duration)` / `transition(duration, easing)` blends every change of the element's look, from states or from your code.

```java
Animation a = box.animate(300ms).ease(EASE_OUT_BACK).offset(0, -10).scale(1.2)
        .then(200ms).offset(0, 0).scale(1)
        .onDone(() -> LOGGER.info("done"));
pulse.animate(600ms).scale(1.3).pingPong().loop();
toast.animate(300ms).delay(2s).transparency(100).onDone(() -> toast.remove());
```

- `animate(duration)` moves the given properties from where they are to the new values. Everything the look setters can set can be animated (colors, lengths, transforms, radius, shadow, ...).
- `then(duration)` starts the next step; `ease(...)` and `delay(...)` apply to the current step.
- `loop()` repeats forever, `repeat(n)` n times, `pingPong()` plays every second round backwards. `cancel()` stops where it is.
- When a step ends, its values stay. A new animation starts from wherever things are.
- The easings are the 31 of easings.net: `LINEAR`, `EASE_IN_SINE` ... `EASE_IN_OUT_BOUNCE` (see [Enums](#enums)).

---

## Events

```java
box.onClick(() -> send("clicked"));
box.onClick(e -> LOGGER.info("at {}, {}", e.x(), e.y()));   // with the event, or without
```

| Event | When |
|---|---|
| `onClick`, `onRightClick` | The left (right) button is pressed and released on the element |
| `onDoubleClick` | The second click of a double click |
| `onPress`, `onRelease` | A mouse button goes down / up (any button, see `e.button()`) |
| `onHover`, `onLeave` | The mouse enters / leaves the element (its children count as inside) |
| `onMouseMove` | The mouse moves over it |
| `onScroll(e -> ...)` | The wheel turns over it; `e.stop()` keeps it from scrolling |
| `onDragStart`, `onDrag` | Dragging a `draggable(true)` element starts / moves |
| `onDrop(e -> ...)` | A dragged element is let go; `e.target()` is what it's over |
| `onFocus`, `onBlur` | It gets / loses keyboard focus |

- Events go from the element under the mouse up through its parents, like on websites. `e.stop()` stops that (and the default: scrolling, Tab, ...).
- The mouse stops at the top-most element that reacts to it (has events, a tooltip, a hover look, a cursor, can be dragged or focused). Elements that don't let it through to what's behind them. `mouseThrough(true/false)` decides it yourself.
- Disabled elements (`disabled(true)`) get no events.
- **MouseEvent:** `x()`, `y()` (in the element whose handler runs), `screenX()`, `screenY()`, `button()` (`LEFT`, `RIGHT`, `MIDDLE`, `OTHER`), `shift()`, `ctrl()`, `alt()`, `cmd()`, `element()`, `target()` (what's actually under the mouse), `stop()`.
- **ScrollEvent:** `x()`, `y()`, `deltaX()`, `deltaY()` (wheel steps, down and right are positive), `shift()`, `ctrl()`, `alt()`, `stop()`.
- **DropEvent:** `element()`, `target()` (may be null), `screenX()`, `screenY()`.
- **Dragging:** `draggable(true)` makes an element follow the mouse (it moves with `offset`, which stays where it was dropped). `dragAxis(X | Y | BOTH)`, `dragInParent(true)` keeps it inside its parent.

### Keys, frames, timers

```java
onKey(e -> { if (e.key().equals("key.keyboard.r")) reload(); });
onKeyRelease(e -> ...);
onFrame(delta -> spinner.rotate(angle += delta * 0.1));   // every frame; delta is milliseconds since the last
onResize(() -> ...);                                        // the window (or GUI scale) changed
Timer t = every(1s, () -> clock.text(formatTime(time(), "HH:mm:ss")));
after(500ms, () -> hint.hide());
t.cancel();
```

- **KeyEvent:** `key()` (vanilla key names like `key.keyboard.a`, `key.keyboard.enter`, `key.mouse.4`), `shift()`, `ctrl()`, `alt()`, `cmd()`, `stop()` (stops Tab from moving the focus). Keys typed into a focused field don't reach `onKey`.
- **Tab** and **Shift+Tab** move the keyboard focus through fields, buttons, sliders and vanilla widgets (`tabIndex(n)` to order them). Enter or Space presses a focused button.

---

## Elements

Created with a function, placed with `add(...)` on the screen or `parent.add(...)` inside another element. `add` gives back what you added, so `Text t = panel.add(text("hi"));` keeps its type.

Every element has: the look setters above, the layout methods, `visible(bool)` / `show()` / `hide()` / `isVisible()`, `disabled(bool)` / `isDisabled()`, `tooltip(text)` (a vanilla tooltip, `String` or `mini(...)`), `cursor(...)`, `remove()`, `clear()` (removes its children), `children()`, `parent()`, `focus()`, `blur()`, `isFocused()`, `isHovered()`, `isPressed()`.

### Box: `box()`

A plain box. Its `color` is its fill, transparent until set.

### Text: `text("...")` or `text(mini("..."))`

- `text(...)` changes it, `text()` gives the plain text.
- `fontSize(px)`, `bold`, `italic`, `underline`, `strikethrough`, `obfuscated` (all `boolean`), `font(DEFAULT | UNIFORM | ALT | ILLAGERALT)` or `font("name")` (a server font), `textShadow(bool)` (on by default, like vanilla), `textShadowColor(color)`.
- `align(LEFT | CENTER | RIGHT)`, `lineSpacing(1.5)`.
- It fits its text on one line. With a `width` or `maxWidth(length)`, it wraps between words; `wrap(false)` keeps it on one line. `\n` starts a new line.

**MiniMessage:** `mini("<red>Hello <bold>there</bold>!")` makes rich text; `text("...")` alone shows exactly what's written (safe for what players typed). The tags:

| Tag | Does |
|---|---|
| `<red>`, `<gold>`, ..., `<#FF8800>`, `<color:#FF8800>` | Color (the 16 vanilla color names) |
| `<bold>` `<b>`, `<italic>` `<i>`, `<underlined>` `<u>`, `<strikethrough>` `<st>`, `<obfuscated>` `<obf>` | Decorations; `<!bold>` turns one off |
| `<gradient:#color1:#color2:...>`, `<rainbow>` | Colors spread over the characters |
| `<font:uniform>`, `<font:name>` | A vanilla or server font |
| `<shadow:#color:0.5>`, `<!shadow>` | Text shadow color (and opacity), or none |
| `<sprite:coin.png>`, `<sprite:minecraft:item/diamond>` | An image in the line (an asset, or a vanilla texture) |
| `<head:Notch>`, `<head:Notch:false>` | A player's face in the line (`false`: without the hat layer) |
| `<newline>`, `<br>` | A new line |
| `<reset>` | Ends every open tag |
| `\<` | A literal `<` |

Unknown tags stay as they are, like MiniMessage.

### Image: `image("coin.png")`, `image("minecraft:block/stone")`, `sprite("widget/button")`

- `image(...)`: an asset PNG by id (registered with `AssetRegistry`), or a vanilla texture as `namespace:path` (`minecraft:item/diamond` is `textures/item/diamond.png`, read from Minecraft's own files, never resource packs). `.mcmeta` animations play.
- `sprite(...)`: a vanilla GUI sprite from the GUI atlas (like `widget/button`); it stretches like vanilla's own (nine-slice or tiled, as the sprite says).
- `mode(STRETCH | FIT | FILL | TILE | NINE_SLICE)`, `nineSlice(left, top, right, bottom)` (texture pixels kept at the corners), `region(u, v, width, height)` (only a part of the texture), `source("...")`.
- Its `color` tints it; `radius` rounds it. Without a size it's as big as the picture.

### Head: `head("Notch")`

A player's face by name, UUID or skin texture value. `hat(false)` leaves out the hat layer, `player("...")` changes it. 16×16 by default.

### Item: `item("minecraft:diamond_sword[enchantments={sharpness:5}]")`

An item written like in `/give`, components included. `count(n)`, `decorations(bool)` (count and durability bar), `itemTooltip(bool)` (vanilla's item tooltip on hover, on by default). 16×16 by default.

### EntityView: `player()`, `entity("minecraft:zombie")`

A living model like the inventory's. It looks at the mouse (`followMouse(true)`), or `rotation(yaw, pitch)` turns it (up to 30 degrees).

### Field: `field()`

A text field. Its `color` is its background, `textColor` the text's.

- `text(...)` / `text()`, `placeholder(text or mini(...))`, `maxLength(n)`, `multiline(true)` (wraps at its width, Enter adds a line), `password(true)`.
- `allow(ANY | DIGITS | DECIMAL | LETTERS | LETTERS_DIGITS)`, `allowChars("abc")` (extra allowed characters; with `ANY`, the only ones).
- `autoFocus(true)` (focused when the screen opens), `keepFocus(true)` (never loses focus), `editable(false)`.
- `suggestion("...")`: gray text after the cursor; Tab or Right takes it.
- `placeholderColor`, `cursorColor`, `selectionColor`, `font(...)`, `fontSize`.
- `selectAll()`, `caret(position)` / `caret()`.
- `onChange(text -> ...)` on every change, `onChange(300ms, text -> ...)` at most every 300 ms (for searches), `onSubmit(text -> ...)` on Enter.
- Keys: arrows, Home/End, word jumps (Alt on Mac, Ctrl elsewhere), Cmd/Ctrl + A/C/X/V, undo Cmd/Ctrl+Z, redo Cmd/Ctrl+Shift+Z (Ctrl+Y too off Mac), mouse selection and double-click on a word.

### Button: `button("Buy")` or `button(mini("<gold>Buy"))`

A box with a centered label, looks for hover, pressed, focused and disabled, and a click sound. Everything can be changed: `color`, `textColor`, `radius`, `hover()`, ... `label(...)` changes the text, `labelText()` gives the label's `Text` to style, `sound("minecraft:ui.button.click")` (or `null` for none). Add anything else to it too (an icon).

### Slider: `slider(min, max)`

`value(v)` / `value()`, `min`, `max`, `step(s)` (snaps to it), `vertical(true)`. `track()`, `fill()` and `knob()` are boxes to style (`knob().size(12, 12).radius(6)`). `onChange(v -> ...)` while it moves, `onDone(v -> ...)` when it's let go. The wheel and the arrow keys (when focused) change it too.

### Canvas: `canvas(g -> ...)`

Its code runs every frame and draws in the canvas (0,0 is its top-left corner, inside the padding). 100×100 by default. `Graphics`:

`width()`, `height()`, `rect(x, y, w, h, color)`, `roundRect(x, y, w, h, radius, color)`, `line(x1, y1, x2, y2, width, color)`, `circle(x, y, radius, color)`, `ring(x, y, radius, width, color)`, `image(source, x, y, w, h)`, `text(text, x, y, color)`, `text(text, x, y, color, size)`. Like borders, line and ring widths can be fractions of a GUI pixel.

### Vanilla widgets

They look and work exactly like vanilla's.

| Function | Methods |
|---|---|
| `vanillaButton("Label")` | `label(text)`, `onClick(...)` |
| `vanillaField()` | `text(...)` / `text()`, `placeholder(text)`, `maxLength(n)`, `editable(bool)`, `bordered(bool)`, `onChange(text -> ...)`, `onSubmit(text -> ...)` |
| `vanillaSlider(min, max)` | `value(v)` / `value()`, `step(s)`, `label(v -> "Volume: " + v)`, `onChange(v -> ...)` |
| `checkbox("Label")` | `checked(bool)` / `checked()`, `label(text)`, `onChange(checked -> ...)` |
| `cycleButton(List.of("A", "B"))` | `values(list)`, `value(v)` / `value()`, `label(text)` ("Label: value"), `onChange(value -> ...)` |

---

## The screen

| Function | Does |
|---|---|
| `add(element)` | Puts an element on the screen (and gives it back) |
| `root()` | The screen itself as a box (for a row or column layout of the whole screen) |
| `blur(0 to 10)` | Blurs the world behind, like the Esc menu (the file's amount, whatever the player's setting) |
| `tint(color)`, `tint(top, bottom)` | Colors the world behind (a gradient with two) |
| `menuBackground(true)` | Vanilla's menu background texture behind |
| `hideHud(true)` | Hides the hotbar, chat and the rest of the HUD |
| `guiScale(n)`, `guiScale()` | Its own GUI scale (0 is the player's again) |
| `pageScrollbar(style)` | The page's scrollbar |
| `focused()` | The element with keyboard focus, or null |
| `close()` | Closes the GUI |
| `onEscape(() -> ...)` | Esc runs this instead of closing |

---

## Talking to the server

**To the server:** `send("name", values...)` sends a message. The plugin gets it with `guis.onMessage("shop.Shop", "name", (player, message) -> ...)` or `SkaffyGuiMessageEvent`. Values can be numbers, text, booleans, chars, lists, maps, records (they arrive as maps of their components), enum constants (their names), colors (ARGB ints) and durations (milliseconds). A message can be at most 32 KB.

**From the server:** every `public` method can be called:

```java
guis.run(player, "shop.Shop", "setCoins", 90);                            // no answer
guis.call(player, "shop.Shop", "total").thenAccept(total -> ...);         // gets the return value
guis.open(player, "shop.Shop", "main", 100, List.of(new Offer("sword", 50)));
```

Values are converted to the parameters: numbers to `int`/`long`/`double`, maps (or Java records) to records by component name, strings to enum constants, ARGB ints or `"#RRGGBB"` to colors, lists and maps element by element.

**Changing code while it runs:** `guis.replaceMethod(player, "shop.Shop", "int bonus() { return 5; }")` gives one method new code for that player, from its next call, until the file is sent again or they leave. It keeps its name, parameters and return type.

**The rest of the plugin API** (`SkaffyGuis`): `addFile(className, source)`, `addFiles(plugin, folder)` (the data folder's files win over the jar's, so server owners can change GUIs), `removeFile`, `getFiles`, `addFont(name, assetId, size, oversample)`, `close(player)`, `openLink(player, url)`, `getOpen(player)`, `isSupported(player)`. Events: `SkaffyGuiOpenEvent` (a screen started), `SkaffyGuiCloseEvent` (with the reason), `SkaffyGuiMessageEvent`, `SkaffyGuiErrorEvent`.

---

## Other functions

| Function | Does |
|---|---|
| `openLink(url)` | Shows vanilla's "open this link?" prompt (always); the GUI comes back afterwards |
| `copy(text)` | Puts text in the clipboard; only right after a click or key press, like browsers |
| `playSound(id)`, `playSound(id, volume, pitch)` | A sound, like `minecraft:ui.button.click` |
| `screenWidth()`, `screenHeight()` | The screen in this GUI's pixels |
| `mouseX()`, `mouseY()` | Where the mouse is |
| `isKeyDown("key.keyboard.left.shift")`, `isMouseDown(LEFT)` | Whether a key or button is held |
| `time()` | Milliseconds since 1970 (a `long`) |
| `formatTime(millis, "HH:mm")` | A time as text, in the player's time zone (Java's DateTimeFormatter patterns) |
| `fps()`, `ping()` | The player's frame rate and ping |
| `language()` | The player's language, like `en_us` |
| `playerName()`, `playerUuid()`, `playerSkin()` | The player themselves (skin: their texture value) |
| `mini(text)` | Rich text from MiniMessage |

---

## Enums

| Enum | Constants |
|---|---|
| `Align` | `START`, `CENTER`, `END`, `STRETCH` |
| `Justify` | `START`, `CENTER`, `END`, `BETWEEN`, `AROUND`, `EVENLY` |
| `TextAlign` | `LEFT`, `CENTER`, `RIGHT` |
| `Cursor` | `DEFAULT`, `HAND`, `TEXT`, `CROSSHAIR`, `RESIZE_EW`, `RESIZE_NS`, `RESIZE_ALL`, `NOT_ALLOWED` (only if the player allows cursor changes) |
| `ImageMode` | `STRETCH`, `FIT`, `FILL`, `TILE`, `NINE_SLICE` |
| `ScrollbarStyle` | `VANILLA`, `MODERN`, `CUSTOM`, `HIDDEN` |
| `Font` | `DEFAULT`, `UNIFORM`, `ALT`, `ILLAGERALT` |
| `Allow` | `ANY`, `DIGITS`, `DECIMAL`, `LETTERS`, `LETTERS_DIGITS` |
| `Axis` | `BOTH`, `X`, `Y` |
| `MouseButton` | `LEFT`, `RIGHT`, `MIDDLE`, `OTHER` |
| `Blend` | `NORMAL`, `INVERT` (see [HUD layers](#hud-layers)) |
| `Easing` | `LINEAR`, `EASE_IN_SINE`, `EASE_OUT_SINE`, `EASE_IN_OUT_SINE`, and the same `IN`/`OUT`/`IN_OUT` for `QUAD`, `CUBIC`, `QUART`, `QUINT`, `EXPO`, `CIRC`, `BACK`, `ELASTIC`, `BOUNCE` |

Every enum constant has `name()` and `ordinal()`.

---

## Limits

- 2 MiB per file, 32 MiB of files per server.
- 16384 elements on screen at once.
- Fonts are TrueType (`.ttf`) assets, added before players join.

---

## Editor support

There's no autocomplete, but `editor/sfy/` has a syntax coloring package:

- **VS Code:** copy the `editor/sfy` folder into `~/.vscode/extensions/` and restart.
- **IntelliJ:** Settings → Editor → TextMate Bundles → `+` → choose the `editor/sfy` folder.

---

## License

- Your `.sfy` files are yours: use any license for them, share them, sell them.
- The `editor/sfy` package may be changed and shared, with credit and a link to the original project.
- Tools that read, check or edit `.sfy` files (editor plugins, language servers, linters, formatters) are welcome under any license. Tools that run `.sfy` files outside Skaffy's API aren't allowed.

The full terms are in LICENSE.
