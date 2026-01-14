#ifndef VIEW_MENU_H
#define VIEW_MENU_H

#include "GuiView.h"

struct MenuItem {
  String title;
  ScreenID target;
  // Optional icon or extra data
};

class MenuView : public GuiView {
public:
  MenuView(GuiController *controller, String title,
           std::vector<MenuItem> items);

  void onEnter() override;
  void onExit() override;
  void onInput(InputEvent event) override;

private:
  String title;
  std::vector<MenuItem> items;
  int selectedIndex;
  int scrollOffset;

  void drawList();
};

#endif
