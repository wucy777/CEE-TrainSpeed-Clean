package com.george_vi.electroenergetics.content.electrical_panel.attachments;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.george_vi.electroenergetics.content.electrical_panel.PanelAttachmentMode;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.AnalogLeverPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.SteeringWheelPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.ThrottleWheelPanelAttachment;
import com.simibubi.create.AllBlocks;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public class CEEPanelAttachmentTypes {
    private static final DeferredRegister<PanelAttachmentType> PANEL_ATTACHMENT_TYPE =
            DeferredRegister.create(CEERegistries.PANEL_ATTACHMENT_TYPE, CreateElectroEnergetics.ID);

    public static final Supplier<PanelAttachmentType> AMMETER = PANEL_ATTACHMENT_TYPE
            .register("ammeter", () -> new PanelAttachmentType(GaugePanelAttachment::ammeter, CEEBlocks.AMMETER, PanelAttachmentMode.HALF_OR_THIRD));

    public static final Supplier<PanelAttachmentType> VOLTMETER = PANEL_ATTACHMENT_TYPE
            .register("voltmeter", () -> new PanelAttachmentType(GaugePanelAttachment::voltmeter, CEEBlocks.VOLTMETER, PanelAttachmentMode.HALF_OR_THIRD));

    public static final Supplier<PanelAttachmentType> FAN = PANEL_ATTACHMENT_TYPE
            .register("fan", () -> new PanelAttachmentType(ElectricFanAttachment::new, CEEBlocks.ELECTRIC_FAN, PanelAttachmentMode.HALF_HORIZONTAL));

    public static final Supplier<PanelAttachmentType> ESTOP = PANEL_ATTACHMENT_TYPE
            .register("emergency_stop_button", () -> new PanelAttachmentType(EStopPanelAttachment::new, CEEBlocks.EMERGENCY_STOP_BUTTON, PanelAttachmentMode.HALF));

    public static final Supplier<PanelAttachmentType> CUT_OFF_SWITCH = PANEL_ATTACHMENT_TYPE
            .register("cut_off_switch", () -> new PanelAttachmentType(CutOffSwitchPanelAttachment::new, CEEBlocks.CUT_OFF_SWITCH, PanelAttachmentMode.HALF_OR_THIRD_OR_SIXTH));

    public static final Supplier<PanelAttachmentType> ANALOG_LEVER = PANEL_ATTACHMENT_TYPE
            .register("analog_lever", () -> new PanelAttachmentType(AnalogLeverPanelAttachment::new, AllBlocks.ANALOG_LEVER, PanelAttachmentMode.HALF_OR_THIRD_OR_SIXTH_NONE));

    public static final Supplier<PanelAttachmentType> THROTTLE_WHEEL = PANEL_ATTACHMENT_TYPE
            .register("throttle_wheel", () -> new PanelAttachmentType(ThrottleWheelPanelAttachment::new, AllBlocks.TURNTABLE, PanelAttachmentMode.QUARTER_NONE));

    public static final Supplier<PanelAttachmentType> STEERING_WHEEL = PANEL_ATTACHMENT_TYPE
            .register("steering_wheel", () -> new PanelAttachmentType(SteeringWheelPanelAttachment::new, AllBlocks.HAND_CRANK, PanelAttachmentMode.QUARTER_NONE));

    public static final Supplier<PanelAttachmentType> INDICATOR_BULB = PANEL_ATTACHMENT_TYPE
            .register("indicator_bulb", () -> new PanelAttachmentType(IndicatorBulbPanelAttachment::new, CEEBlocks.INDICATOR_BULB, PanelAttachmentMode.HALF_OR_THIRD_OR_SIXTH));

    public static final Supplier<PanelAttachmentType> MOMENTARY_SWITCH = PANEL_ATTACHMENT_TYPE
            .register("momentary_switch", () -> new PanelAttachmentType(MomentarySwitchPanelAttachment::new, CEEBlocks.MOMENTARY_SWITCH, PanelAttachmentMode.HALF_OR_THIRD_OR_SIXTH));

    public static final Supplier<PanelAttachmentType> ENERGY_METER = PANEL_ATTACHMENT_TYPE
            .register("energy_meter", () -> new PanelAttachmentType(EnergyMeterAttachment::new, CEEBlocks.ENERGY_METER, PanelAttachmentMode.FULL_DOUBLE));

    public static final Supplier<PanelAttachmentType> TRI_POLAR_ENERGY_METER = PANEL_ATTACHMENT_TYPE
            .register("tri_polar_energy_meter", () -> new PanelAttachmentType(TriPolarEnergyMeterAttachment::new, CEEBlocks.TRI_POLAR_ENERGY_METER, PanelAttachmentMode.FULL_TRIPLE));

    public static final Supplier<PanelAttachmentType> MINIATURE_CIRCUIT_BREAKER = PANEL_ATTACHMENT_TYPE
            .register("miniature_circuit_breaker", () -> new PanelAttachmentType(MCBPanelAttachment::new, CEEItems.MINIATURE_CIRCUIT_BREAKER, PanelAttachmentMode.THIRD));

    public static final Supplier<PanelAttachmentType> TRANSFORMER = PANEL_ATTACHMENT_TYPE
            .register("transformer", () -> new PanelAttachmentType(TransformerAttachment::new, CEEBlocks.TRANSFORMER, PanelAttachmentMode.SIXTH_QUAD));

    public static final Supplier<PanelAttachmentType> ALTITUDE_SENSOR = PANEL_ATTACHMENT_TYPE
            .register("altitude_sensor", () -> new PanelAttachmentType(AltitudeSensorPanelAttachment::new, simItem(() -> SimBlocks.ALTITUDE_SENSOR), PanelAttachmentMode.FULL_NONE));

    public static final Supplier<PanelAttachmentType> VELOCITY_SENSOR = PANEL_ATTACHMENT_TYPE
            .register("velocity_sensor", () -> new PanelAttachmentType(VelocitySensorPanelAttachment::new, simItem(() -> SimBlocks.VELOCITY_SENSOR), PanelAttachmentMode.HALF_NONE));


    private static ItemLike simItem(Supplier<ItemLike> sup) {
        if (ModList.get().isLoaded("simulated"))
            return sup.get();
        return Items.AIR;
    }

    public static void register(IEventBus bus) {
        PANEL_ATTACHMENT_TYPE.register(bus);
    }

}
