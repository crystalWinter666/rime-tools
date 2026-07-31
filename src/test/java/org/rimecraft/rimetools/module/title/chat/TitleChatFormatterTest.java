package org.rimecraft.rimetools.module.title.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleChatFormatterTest {
    @Test
    void formatsChatWithExactlyOneSpaceInsideBrackets() {
        Component result = TitleChatFormatter.format(
                Component.literal("MVP").withStyle(style -> style.withColor(0x55FFFF)),
                Component.literal("Alice"),
                Component.literal("Hello")
        );

        assertEquals("[ MVP ] Alice: Hello", result.getString());
        List<Component> flat = result.toFlatList();
        assertEquals(0x55FFFF, flat.stream()
                .filter(component -> component.getString().equals("MVP"))
                .findFirst()
                .orElseThrow()
                .getStyle().getColor().getValue());
        assertEquals(0x55FFFF, flat.stream()
                .filter(component -> component.getString().equals("[ "))
                .findFirst()
                .orElseThrow()
                .getStyle().getColor().getValue());
        assertEquals(0x55FFFF, flat.stream()
                .filter(component -> component.getString().equals(" ]"))
                .findFirst()
                .orElseThrow()
                .getStyle().getColor().getValue());
    }
}
