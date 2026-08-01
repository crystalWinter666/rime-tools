package org.rimecraft.rimetools.module.punishment;

import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;

/** Translatable moderation messages with a readable fallback for vanilla clients. */
public final class PunishmentText {
    private PunishmentText() { }

    public static Component banned(PunishmentRecord record) {
        if (record.reason() == null) {
            return Component.translatableWithFallback("rime-tools.punish.login.banned",
                    "You have been permanently banned (by " + record.executor() + ")", record.executor());
        }
        return Component.translatableWithFallback("rime-tools.punish.login.banned_reason",
                reason("You have been permanently banned by " + record.executor(), record.reason()),
                record.executor(), record.reason());
    }

    public static Component tempBanned(PunishmentRecord record, long remaining) {
        String formatted = DurationFormatter.format(remaining);
        if (record.reason() == null) {
            return Component.translatableWithFallback("rime-tools.punish.login.tempbanned",
                    "You have been temporarily banned by " + record.executor() + " (" + formatted + " remaining)",
                    formatted, record.executor());
        }
        return Component.translatableWithFallback("rime-tools.punish.login.tempbanned_reason",
                reason("You have been temporarily banned by " + record.executor()
                        + " (" + formatted + " remaining)", record.reason()),
                formatted, record.executor(), record.reason());
    }

    public static Component kicked(PunishmentRecord record) {
        String fallback = reason("You have been kicked by " + record.executor(), record.reason());
        return record.reason() == null
                ? Component.translatableWithFallback("rime-tools.punish.kick.disconnect", fallback, record.executor())
                : Component.translatableWithFallback("rime-tools.punish.kick.disconnect_reason", fallback,
                        record.executor(), record.reason());
    }

    public static Component warned(PunishmentRecord record) {
        return Component.translatableWithFallback("rime-tools.punish.warn.received",
                reason("You received a warning from " + record.executor(), record.reason()),
                record.executor(), record.reason() == null ? "-" : record.reason());
    }

    public static Component muted(PunishmentRecord record, long remaining) {
        String reason = record.reason() == null ? "-" : record.reason();
        if (remaining < 0) {
            return Component.translatableWithFallback("rime-tools.punish.muted.permanent_detail",
                    "You are permanently muted by " + record.executor() + ": " + reason,
                    record.executor(), reason);
        }
        return Component.translatableWithFallback("rime-tools.punish.muted.detail",
                "You are muted (" + DurationFormatter.format(remaining) + " remaining) by "
                        + record.executor() + ": " + reason,
                DurationFormatter.format(remaining), record.executor(), reason);
    }

    public static Component muted(long remaining) {
        if (remaining < 0) {
            return Component.translatableWithFallback("rime-tools.punish.muted.permanent",
                    "You are permanently muted");
        }
        return Component.translatableWithFallback("rime-tools.punish.muted.online",
                "You are muted (" + DurationFormatter.format(remaining) + " remaining)",
                DurationFormatter.format(remaining));
    }

    private static String reason(String base, String reason) {
        return reason == null || reason.isBlank() ? base : base + ": " + reason;
    }
}
