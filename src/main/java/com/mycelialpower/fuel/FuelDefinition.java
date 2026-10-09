package com.mycelialpower.fuel;

/**
 * One configured mushroom fuel entry. Pure data: resolution against the item registry happens in
 * {@link FuelRegistry}.
 *
 * @param id               item id ({@code minecraft:red_mushroom}) or tag id ({@code c:mushrooms}) without the {@code #}
 * @param tag              whether {@link #id} names an item tag
 * @param burnTicks        ticks of generation provided by one item
 * @param energyMultiplier multiplier applied to the base FE/t while this fuel burns
 * @param waterMultiplier  multiplier applied to water consumption while this fuel burns
 * @param myceliumBonus    multiplier applied to the mycelium chance growth per failed attempt (1 = no bonus)
 * @param scalingBonus     added to the network scaling multiplier for this generator's own output
 */
public record FuelDefinition(String id, boolean tag, int burnTicks, double energyMultiplier, double waterMultiplier,
                             double myceliumBonus, double scalingBonus) {

    public static final FuelDefinition NONE = new FuelDefinition("minecraft:air", false, 0, 0, 0, 1, 0);

    /** Canonical config string for this definition, as accepted by {@link FuelEntryParser}. */
    public String toConfigString() {
        return (tag ? "#" : "") + id
                + ";burn=" + burnTicks
                + ";energy=" + FuelEntryParser.formatNumber(energyMultiplier)
                + ";water=" + FuelEntryParser.formatNumber(waterMultiplier)
                + ";mycelium=" + FuelEntryParser.formatNumber(myceliumBonus)
                + ";scaling=" + FuelEntryParser.formatNumber(scalingBonus);
    }
}
