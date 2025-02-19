package net.earthcomputer.clientcommands;

import dev.xpple.betterconfig.api.Config;
import net.earthcomputer.clientcommands.command.ReplyCommand;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public class Configs {

    @Config(readOnly = true, temporary = true)
    public static double calcAnswer = 0;

    @Config(readOnly = true, temporary = true)
    public static EnchantmentCracker.CrackState enchCrackState = EnchantmentCracker.CrackState.UNCRACKED;

    @Config(readOnly = true, temporary = true)
    public static PlayerRandCracker.CrackState playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;

    @Config(onChange = "onChangeEnchantingPrediction", temporary = true)
    public static boolean enchantingPrediction = false;
    private static void onChangeEnchantingPrediction(boolean oldEnchantingPrediction, boolean enchantingPrediction) {
        if (enchantingPrediction) {
            ServerBrandManager.rngWarning();
        } else {
            EnchantmentCracker.resetCracker();
        }
    }

    public enum FishingManipulation implements StringRepresentable {
        OFF,
        MANUAL,
        AFK;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public boolean isEnabled() {
            return this != OFF;
        }
    }

    @Config(onChange = "onChangeFishingManipulation", temporary = true, condition = "conditionLessThan1_20")
    public static FishingManipulation fishingManipulation = FishingManipulation.OFF;
    private static void onChangeFishingManipulation(FishingManipulation oldFishingManipulation, FishingManipulation fishingManipulation) {
        if (fishingManipulation.isEnabled()) {
            ServerBrandManager.rngWarning();
        } else {
            FishingCracker.reset();
        }
    }

    @Config(temporary = true)
    public static boolean playerRNGMaintenance = true;

    @Config
    public static boolean toolBreakWarning = false;

    @Config(setter = @Config.Setter("setMaxEnchantItemThrows"))
    private static int maxEnchantItemThrows = 64 * 256;
    public static int getMaxEnchantItemThrows() {
        return maxEnchantItemThrows;
    }
    public static void setMaxEnchantItemThrows(int maxEnchantItemThrows) {
        Configs.maxEnchantItemThrows = Mth.clamp(maxEnchantItemThrows, 0, 1000000);
    }

    @Config(setter = @Config.Setter("setMinEnchantBookshelves"), temporary = true)
    private static int minEnchantBookshelves = 0;
    public static int getMinEnchantBookshelves() {
        return minEnchantBookshelves;
    }
    public static void setMinEnchantBookshelves(int minEnchantBookshelves) {
        Configs.minEnchantBookshelves = Mth.clamp(minEnchantBookshelves, 0, 15);
        Configs.maxEnchantBookshelves = Math.max(Configs.maxEnchantBookshelves, Configs.minEnchantBookshelves);
    }

    @Config(setter = @Config.Setter("setMaxEnchantBookshelves"), temporary = true)
    private static int maxEnchantBookshelves = 15;
    public static int getMaxEnchantBookshelves() {
        return maxEnchantBookshelves;
    }
    public static void setMaxEnchantBookshelves(int maxEnchantBookshelves) {
        Configs.maxEnchantBookshelves = Mth.clamp(maxEnchantBookshelves, 0, 15);
        Configs.minEnchantBookshelves = Math.min(Configs.minEnchantBookshelves, Configs.maxEnchantBookshelves);
    }

    @Config(setter = @Config.Setter("setMinEnchantLevels"), temporary = true)
    private static int minEnchantLevels = 1;
    public static int getMinEnchantLevels() {
        return minEnchantLevels;
    }
    public static void setMinEnchantLevels(int minEnchantLevels) {
        Configs.minEnchantLevels = Mth.clamp(minEnchantLevels, 1, 30);
        Configs.maxEnchantLevels = Math.max(Configs.maxEnchantLevels, Configs.minEnchantLevels);
    }

    @Config(setter = @Config.Setter("setMaxEnchantSlot"), temporary = true)
    private static int maxEnchantSlot = 3;

    public static int getMaxEnchantSlot() {
        return maxEnchantSlot;
    }

    public static void setMaxEnchantSlot(int minEnchantLevels) {
        Configs.maxEnchantSlot = Mth.clamp(maxEnchantSlot, 1, 3);
    }

    @Config(setter = @Config.Setter("setMaxEnchantLevels"), temporary = true)
    private static int maxEnchantLevels = 30;
    public static int getMaxEnchantLevels() {
        return maxEnchantLevels;
    }
    public static void setMaxEnchantLevels(int maxEnchantLevels) {
        Configs.maxEnchantLevels = Mth.clamp(maxEnchantLevels, 1, 30);
        Configs.minEnchantLevels = Math.min(Configs.minEnchantLevels, Configs.maxEnchantLevels);
    }

    @Config(onChange = "onChangeChorusManipulation", temporary = true)
    public static boolean chorusManipulation = false;
    public static void onChangeChorusManipulation(boolean oldChorusManipulation, boolean chorusManipulation) {
        if (chorusManipulation) {
            ServerBrandManager.rngWarning();
            ChorusManipulation.onChorusManipEnabled();
        }
    }

    @Config(setter = @Config.Setter("setMaxChorusItemThrows"))
    private static int maxChorusItemThrows = 64 * 32;
    public static int getMaxChorusItemThrows() {
        return maxChorusItemThrows;
    }
    public static void setMaxChorusItemThrows(int maxChorusItemThrows) {
        Configs.maxChorusItemThrows = Mth.clamp(maxChorusItemThrows, 0, 1000000);
    }

    @Config(temporary = true)
    public static String autoPrefix = "";

    @Config(temporary = true, condition = "conditionLessThan1_21")
    public static boolean infiniteTools = false;

    @Config
    public static int commandExecutionLimit = 65536;

    @Config
    public static boolean acceptC2CPackets = false;

    @Config
    public static float itemThrowsPerTick = 1;

    public static boolean conditionLessThan1_20() {
        return MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_20;
    }

    public static boolean conditionLessThan1_21() {
        return MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_21;
    }

    @Config
    public static PacketDumpMethod packetDumpMethod = PacketDumpMethod.REFLECTION;

    public enum PacketDumpMethod {
        REFLECTION,
        BYTE_BUF,
    }

    @Config
    public static int maximumPacketFieldDepth = 10;

    @Config(temporary = true, setter = @Config.Setter("setMinimumReplyDelaySeconds"))
    public static float minimumReplyDelaySeconds = 0.5f;
    public static void setMinimumReplyDelaySeconds(float minimumReplyDelaySeconds) {
        Configs.minimumReplyDelaySeconds = Math.clamp(minimumReplyDelaySeconds, 0.0f, ReplyCommand.MAXIMUM_REPLY_DELAY_SECONDS);
    }
}
