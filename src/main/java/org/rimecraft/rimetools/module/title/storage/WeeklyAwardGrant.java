package org.rimecraft.rimetools.module.title.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

public record WeeklyAwardGrant(UUID playerId, List<String> titleIds) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<WeeklyAwardGrant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("player_id").forGetter(WeeklyAwardGrant::playerId),
            Codec.STRING.listOf().fieldOf("title_ids").forGetter(WeeklyAwardGrant::titleIds)
    ).apply(instance, WeeklyAwardGrant::new));

    public WeeklyAwardGrant {
        titleIds = List.copyOf(titleIds);
    }
}
