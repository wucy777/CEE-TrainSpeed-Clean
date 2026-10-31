package com.george_vi.electroenergetics;

import com.george_vi.electroenergetics.content.electrical_panel.link.ElectricalPanelLinkMenu;
import com.george_vi.electroenergetics.content.electrical_panel.link.ElectricalPanelLinkScreen;
import com.george_vi.electroenergetics.content.electrical_panel.link.BiDirectionalLinkMenu;
import com.george_vi.electroenergetics.content.electrical_panel.link.BiDirectionalLinkScreen;
import com.tterrag.registrate.util.entry.MenuEntry;

public class CEEMenuTypes {
    public static final MenuEntry<ElectricalPanelLinkMenu> ELECTRICAL_PANEL_LINK_MENU = CreateElectroEnergetics.REGISTRATE
            .menu("electrical_panel_link", ElectricalPanelLinkMenu::new, () -> ElectricalPanelLinkScreen::new)
            .register();

    public static final MenuEntry<BiDirectionalLinkMenu> BIDIRECTIONAL_LINK_MENU = CreateElectroEnergetics.REGISTRATE
            .menu("bidirectional_link", BiDirectionalLinkMenu::new, () -> BiDirectionalLinkScreen::new)
            .register();


    public static void register() {
    }
}
