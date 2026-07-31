package org.rimecraft.rimetools.module.title.title;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.UUID;

public record TitleSelection(UUID player, String titleId) {
    public static final Codec<TitleSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("player").forGetter(TitleSelection::player),
            Codec.STRING.fieldOf("title").forGetter(TitleSelection::titleId)
    ).apply(instance, TitleSelection::new));

    public TitleSelection {
        Objects.requireNonNull(player, "player");
        if (!TitleInputValidator.isValidId(titleId)) {
            throw new IllegalArgumentException("Invalid selected title id: " + titleId);
        }
    }
}
