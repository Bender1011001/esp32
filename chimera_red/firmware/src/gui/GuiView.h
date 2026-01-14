#ifndef GUI_VIEW_H
#define GUI_VIEW_H

#include "GuiController.h"

// Base class for all screens/apps
class GuiView {
public:
  GuiView(GuiController *controller) : gui(controller) {}
  virtual ~GuiView() {}

  // Lifecycle
  virtual void onEnter() = 0;
  virtual void onExit() = 0;
  virtual void onUpdate() {}                // Called every loop
  virtual void onInput(InputEvent event) {} // Input handler

protected:
  GuiController *gui;

  // Helper to draw common header
  void drawHeader(String title);
};

#endif
