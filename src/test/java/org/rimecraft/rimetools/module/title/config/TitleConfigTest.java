package org.rimecraft.rimetools.module.title.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleConfigTest {
    @Test
    void usesConfiguredFallbackTitle() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".yml");
        Files.writeString(file, "default-title: 访客\n" + "default-color: \"#FFAA00\"\n");

        TitleConfig config = TitleConfig.load(file);

        assertEquals("访客", config.defaultTitle());
        assertEquals("#FFAA00", config.defaultColor());
    }

    @Test
    void usesSafeDefaultsForMissingOrInvalidValues() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".yml");
        Files.writeString(file, "default-title: \"\"\n" + "default-color: not-a-color\n");

        TitleConfig config = TitleConfig.load(file);

        assertEquals("玩家", config.defaultTitle());
        assertEquals("#AAAAAA", config.defaultColor());
    }

    @Test
    void malformedYamlDoesNotPreventStartup() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".yml");
        Files.writeString(file, "default-title: [\n");

        TitleConfig config = TitleConfig.load(file);

        assertEquals("玩家", config.defaultTitle());
        assertEquals("#AAAAAA", config.defaultColor());
    }

    @Test
    void loadsWeeklyRankAwardSchedule() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".yml");
        Files.writeString(file, "weekly-rank-awards:\n"
                + "  enabled: true\n"
                + "  day: SUNDAY\n"
                + "  time: \"23:30\"\n"
                + "  zone: Asia/Shanghai\n");

        TitleConfig config = TitleConfig.load(file);

        assertEquals(true, config.weeklyRankAwards().enabled());
        assertEquals(DayOfWeek.SUNDAY, config.weeklyRankAwards().day());
        assertEquals(LocalTime.of(23, 30), config.weeklyRankAwards().time());
        assertEquals(ZoneId.of("Asia/Shanghai"), config.weeklyRankAwards().zone());
    }

    @Test
    void loadsMonthlyRankAwardSchedule() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".yml");
        Files.writeString(file, "monthly-rank-awards:\n"
                + "  enabled: true\n"
                + "  day: 1\n"
                + "  time: \"00:05\"\n"
                + "  zone: Asia/Shanghai\n");

        TitleConfig config = TitleConfig.load(file);

        assertEquals(true, config.monthlyRankAwards().enabled());
        assertEquals(1, config.monthlyRankAwards().dayOfMonth());
        assertEquals(LocalTime.of(0, 5), config.monthlyRankAwards().time());
        assertEquals(ZoneId.of("Asia/Shanghai"), config.monthlyRankAwards().zone());
    }

    @Test
    void migratesLegacyProperties() throws Exception {
        Path file = Files.createTempFile("rime-tools", ".properties");
        Files.writeString(file, "default-title=访客\n"
                + "default-color=#FFAA00\n"
                + "weekly-rank-awards-enabled=true\n"
                + "weekly-rank-awards-day=SUNDAY\n"
                + "weekly-rank-awards-time=23:30\n"
                + "weekly-rank-awards-zone=Asia/Shanghai\n"
                + "monthly-rank-awards-enabled=false\n"
                + "monthly-rank-awards-day=5\n"
                + "monthly-rank-awards-time=01:02\n"
                + "monthly-rank-awards-zone=Asia/Tokyo\n");

        TitleConfig config = TitleConfig.fromProperties(file);

        assertEquals("访客", config.defaultTitle());
        assertEquals("#FFAA00", config.defaultColor());
        assertEquals(DayOfWeek.SUNDAY, config.weeklyRankAwards().day());
        assertEquals(LocalTime.of(23, 30), config.weeklyRankAwards().time());
        assertEquals(false, config.monthlyRankAwards().enabled());
        assertEquals(5, config.monthlyRankAwards().dayOfMonth());
        assertEquals(LocalTime.of(1, 2), config.monthlyRankAwards().time());
        assertEquals(ZoneId.of("Asia/Tokyo"), config.monthlyRankAwards().zone());
    }
}
