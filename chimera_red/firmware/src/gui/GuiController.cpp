#include "GuiController.h"
#include "GuiView.h"
#include "ViewMenu.h"

// Instance
GuiController GUI;

// -- Views --
// We will instantiate views on demand or keep singletons if memory permits.
// For now, let's keep it simple: create on demand? Or static instances?
// Static instances avoid fragmentation.

// -- Mock Sub-Views (TODO: Move to separate files) --
class WifiScanView : public GuiView {
public:
  using GuiView::GuiView;
  void onEnter() override {
    drawHeader("WiFi Scanner");
    gui->getDisplay()->println("Scanning..."); /* Hardware Hook Here */
  }
  void onExit() override {}
};

// -- Implementation --

GuiController::GuiController()
    : tft(TFT_eSPI()), currentView(NULL), currentScreenId(SCREEN_ROOT) {}

void GuiController::begin() {
  // Initialized in main.cpp typically, but we can do it here too if needed.
  // tft.init();
  // tft.setRotation(1);

  // Load Root
  loadView(SCREEN_ROOT);
}

void GuiController::update() {
  if (currentView) {
    currentView->onUpdate();
  }
}

void GuiController::handleInput(InputEvent event) {
  if (event == INPUT_BACK && currentScreenId != SCREEN_ROOT) {
    // Global back handler if view doesn't consume it?
    // Actually, let view handle it first.
  }

  if (currentView) {
    currentView->onInput(event);
  }
}

void GuiController::navigateTo(ScreenID screen) {
  if (currentScreenId != screen) {
    history.push_back(currentScreenId);
    loadView(screen);
  }
}

void GuiController::goBack() {
  if (!history.empty()) {
    ScreenID prev = history.back();
    history.pop_back();
    loadView(prev);
  }
}

void GuiController::loadView(ScreenID screen) {
  // Clean up current?
  if (currentView) {
    currentView->onExit();
    delete currentView;
    currentView = NULL;
  }

  currentScreenId = screen;
  TFT_eSPI *d = &tft;
  d->fillScreen(COLOR_BG); // Clear screen on transition

  // FACTORY (This should be generated from features.json)
  switch (screen) {
  case SCREEN_ROOT: {
    std::vector<MenuItem> rootItems = {
        {"WiFi Tools", SCREEN_WIFI_SCAN},
        {"Bluetooth", SCREEN_BLE_SCAN},
        {"Sub-GHz", SCREEN_RF_SPECTRUM},
        {"NFC", SCREEN_NFC_READ},
        {"System", SCREEN_ROOT} // Placeholder
    };
    currentView = new MenuView(this, "Main Menu", rootItems);
    break;
  }
  case SCREEN_WIFI_SCAN:
    currentView = new WifiScanView(this);
    break;
  // ... Add others
  default:
    currentView = new WifiScanView(this); // Fallback
    break;
  }

  if (currentView) {
    currentView->onEnter();
  }
}

// -- View Implementation --

void GuiView::drawHeader(String title) {
  TFT_eSPI *t = gui->getDisplay();
  t->fillRect(0, 0, 240, 24, COLOR_SURFACE);
  t->setTextColor(COLOR_SECONDARY, COLOR_SURFACE);
  t->drawCentreString(title, 120, 4, FONT_BASE);
  t->drawFastHLine(0, 24, 240, COLOR_PRIMARY);
}

// -- Menu View Implementation --

MenuView::MenuView(GuiController *c, String t, std::vector<MenuItem> i)
    : GuiView(c), title(t), items(i), selectedIndex(0), scrollOffset(0) {}

void MenuView::onEnter() {
  drawHeader(title);
  drawList();
}

void MenuView::onExit() {
  // Cleanup if needed
}

void MenuView::onInput(InputEvent event) {
  switch (event) {
  case INPUT_UP:
    if (selectedIndex > 0)
      selectedIndex--;
    drawList();
    break;
  case INPUT_DOWN:
    if (selectedIndex < items.size() - 1)
      selectedIndex++;
    drawList();
    break;
  case INPUT_SELECT:
    gui->navigateTo(items[selectedIndex].target);
    break;
  case INPUT_BACK:
    if (gui)
      gui->goBack();
    break;
  default:
    break;
  }
}

void MenuView::drawList() {
  TFT_eSPI *t = gui->getDisplay();
  int startY = 30;
  int itemH = 30;
  int maxItems = 7;

  // Basic scrolling logic (can be improved)
  int renderStart = 0;

  for (int i = 0; i < items.size(); i++) {
    int y = startY + (i * itemH);

    // Highlight
    if (i == selectedIndex) {
      t->fillRect(0, y, 240, itemH, COLOR_SURFACE);
      t->setTextColor(COLOR_PRIMARY, COLOR_SURFACE);
      t->drawString("> " + items[i].title, 10, y + 8, FONT_BASE);
    } else {
      t->fillRect(0, y, 240, itemH, COLOR_BG);
      t->setTextColor(COLOR_TEXT, COLOR_BG);
      t->drawString("  " + items[i].title, 10, y + 8, FONT_BASE);
    }
  }
}
