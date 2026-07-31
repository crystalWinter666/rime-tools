package org.rimecraft.rimetools.module.title.title;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TitleDefinition(String id, String displayName, String color, int weight, boolean enabled, boolean gradient) {
    private static final int[] RAINBOW = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF
    };
    public static final Codec<TitleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TitleDefinition::id),
            Codec.STRING.fieldOf("display_name").forGetter(TitleDefinition::displayName),
            Codec.STRING.fieldOf("color").forGetter(TitleDefinition::color),
            Codec.INT.fieldOf("weight").forGetter(TitleDefinition::weight),
            Codec.BOOL.fieldOf("enabled").forGetter(TitleDefinition::enabled),
            Codec.BOOL.optionalFieldOf("gradient", false).forGetter(TitleDefinition::gradient)
    ).apply(instance, TitleDefinition::new));

    public TitleDefinition(String id, String displayName, String color, int weight, boolean enabled) {
        this(id, displayName, color, weight, enabled, false);
    }

    public TitleDefinition {
        if (!TitleInputValidator.isValidId(id)) {
            throw new IllegalArgumentException("Invalid title id: " + id);
        }
        if (!TitleInputValidator.isValidDisplayName(displayName)) {
            throw new IllegalArgumentException("Invalid title display name");
        }
        String originalColor = color;
        color = TitleInputValidator.normalizeColor(color)
                .orElseThrow(() -> new IllegalArgumentException("Invalid title color: " + originalColor));
        if (weight < -100_000 || weight > 100_000) {
            throw new IllegalArgumentException("Title weight is out of range");
        }
    }

    public Component asComponent() {
        if (gradient) {
            MutableComponent result = Component.empty();
            int[] codePoints = displayName.codePoints().toArray();
            for (int index = 0; index < codePoints.length; index++) {
                int characterColor = RAINBOW[index % RAINBOW.length];
                result.append(Component.literal(new String(Character.toChars(codePoints[index])))
                        .withStyle(style -> style.withColor(characterColor)));
            }
            return result;
        }
        return Component.literal(displayName).withStyle(style -> style.withColor(TextColor.parseColor(color).getOrThrow()));
    }
}
