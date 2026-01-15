#ifndef GUI_CONTROLLER_H
#define GUI_CONTROLLER_H

#include "GuiCommon.h"
#include <Arduino.h>
#include <TFT_eSPI.h>
#include <vector>

// Forward decl
class GuiView;

class GuiController {
public:
  GuiController();
  void begin();
  void update();

  // Input handling
  void handleInput(InputEvent event);

  // Navigation
  void navigateTo(ScreenID screen);
  void goBack();

  // Drawing
  TFT_eSPI *getDisplay() { return &tft; }

private:
  TFT_eSPI tft;
  GuiView *currentView;
  std::vector<ScreenID> history;

  ScreenID currentScreenId;

  void loadView(ScreenID screen);
};

extern GuiController GUI;

#endif
