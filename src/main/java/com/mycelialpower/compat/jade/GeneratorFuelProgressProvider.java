package com.mycelialpower.compat.jade;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.blockentity.GeneratorState;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.menu.GeneratorStatus;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ProgressView;
import snownee.jade.api.view.ViewGroup;

/** Jade progress bar for the mushroom currently burning (fills as it burns down). */
public enum GeneratorFuelProgressProvider implements IServerExtensionProvider<ProgressView.Data>,
        IClientExtensionProvider<ProgressView.Data, ProgressView> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(MycelialPower.MOD_ID, "generator_fuel");

    @Override
    public @Nullable List<ViewGroup<ProgressView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || !(block.getBlockEntity() instanceof MycelialGeneratorBlockEntity generator)) return null;
        GeneratorStatus s = generator.createStatus();
        if (s.burnTotal() <= 0 || s.burnRemaining() <= 0) return null;
        float progress = 1.0F - (float) s.burnRemaining() / s.burnTotal();
        float speed = GeneratorState.byOrdinal(s.state()) == GeneratorState.RUNNING ? 1.0F / s.burnTotal() : 0.0F;
        return List.of(new ViewGroup<>(List.of(new ProgressView.Data(progress, speed, 1.0F))));
    }

    @Override
    public List<ClientViewGroup<ProgressView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ProgressView.Data>> groups) {
        return ClientViewGroup.map(groups, ProgressView::read, null);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
