package net.earthcomputer.clientcommands.c2c;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutConnectFourPieceC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.PutTicTacToeMarkC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StartTwoPlayerGameC2CPacket;
import net.earthcomputer.clientcommands.command.ConnectFourCommand;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.earthcomputer.clientcommands.command.TicTacToeCommand;
import net.earthcomputer.clientcommands.command.arguments.ExtendedMarkdownArgument;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class C2CPacketHandler implements C2CPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new DynamicCommandExceptionType(d -> Component.translatable("c2cpacket.messageTooLong", d));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.publicKeyNotFound"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("c2cpacket.encryptionFailed"));

    public static final ProtocolInfo<C2CPacketListener> C2C = ProtocolInfoBuilder.<C2CPacketListener, C2CFriendlyByteBuf>clientboundProtocol(ConnectionProtocol.PLAY, builder -> builder
        .addPacket(MessageC2CPacket.ID, MessageC2CPacket.CODEC)
        .addPacket(StartTwoPlayerGameC2CPacket.ID, StartTwoPlayerGameC2CPacket.CODEC)
        .addPacket(PutTicTacToeMarkC2CPacket.ID, PutTicTacToeMarkC2CPacket.CODEC)
        .addPacket(PutConnectFourPieceC2CPacket.ID, PutConnectFourPieceC2CPacket.CODEC)
    ).bind(b -> (C2CFriendlyByteBuf) b);

    public static final String C2C_PACKET_HEADER = "CCΕNC:";

    private static final C2CPacketHandler instance = new C2CPacketHandler();

    private C2CPacketHandler() {
    }

    public static C2CPacketHandler getInstance() {
        return instance;
    }

    public void sendPacket(Packet<C2CPacketListener> packet, PlayerInfo recipient) throws CommandSyntaxException {
        RemoteChatSession session = recipient.getChatSession();
        if (session == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        ProfilePublicKey ppk = session.profilePublicKey();
        //noinspection ConstantValue
        if (ppk == null) {
            throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
        }
        PublicKey key = ppk.data().key();
        FriendlyByteBuf buf = wrapByteBuf(PacketByteBufs.create(), null, null);
        if (buf == null) {
            return;
        }
        C2C.codec().encode(buf, packet);
        byte[] uncompressed = new byte[buf.readableBytes()];
        buf.getBytes(0, uncompressed);
        byte[] compressed = ConversionHelper.Gzip.compress(uncompressed);
        if (compressed == null) {
            return;
        }
        // split compressed into 245 byte chunks
        int chunks = (compressed.length + 244) / 245;
        byte[][] chunked = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            int start = i * 245;
            int end = Math.min(start + 245, compressed.length);
            chunked[i] = new byte[end - start];
            System.arraycopy(compressed, start, chunked[i], 0, end - start);
        }
        // encrypt each chunk
        byte[][] encrypted = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            encrypted[i] = ConversionHelper.RsaEcb.encrypt(chunked[i], key);
            if (encrypted[i] == null || encrypted[i].length == 0) {
                throw ENCRYPTION_FAILED_EXCEPTION.create();
            }
        }
        // join encrypted chunks into one byte array
        byte[] joined = new byte[encrypted.length * 256];
        for (int i = 0; i < encrypted.length; i++) {
            System.arraycopy(encrypted[i], 0, joined, i * 256, 256);
        }
        String packetString = ConversionHelper.BaseUTF8.toUnicode(joined);
        String commandString = "w " + recipient.getProfile().getName() + ' ' + C2C_PACKET_HEADER + packetString;
        if (commandString.length() >= SharedConstants.MAX_CHAT_LENGTH) {
            throw MESSAGE_TOO_LONG_EXCEPTION.create(commandString.length());
        }
        ListenCommand.onPacket(packet, ListenCommand.PacketFlow.C2C_OUTBOUND);
        Minecraft.getInstance().getConnection().sendCommand(commandString);
        OutgoingPacketFilter.addPacket(packetString);
    }

    public static boolean handleC2CPacket(String content, String sender, UUID senderUUID) {
        byte[] encrypted = ConversionHelper.BaseUTF8.fromUnicode(content);
        // round down to multiple of 256 bytes
        int length = encrypted.length & ~0xFF;
        // copy to new array of arrays
        byte[][] encryptedArrays = new byte[length / 256][];
        for (int i = 0; i < length; i += 256) {
            encryptedArrays[i / 256] = Arrays.copyOfRange(encrypted, i, i + 256);
        }
        if (!(Minecraft.getInstance().getProfileKeyPairManager() instanceof AccountProfileKeyPairManager profileKeyPairManager)) {
            return false;
        }
        Optional<ProfileKeyPair> keyPair = profileKeyPairManager.keyPair.join();
        if (keyPair.isEmpty()) {
            return false;
        }
        // decrypt
        int len = 0;
        byte[][] decryptedArrays = new byte[encryptedArrays.length][];
        for (int i = 0; i < encryptedArrays.length; i++) {
            decryptedArrays[i] = ConversionHelper.RsaEcb.decrypt(encryptedArrays[i], keyPair.get().privateKey());
            if (decryptedArrays[i] == null) {
                return false;
            }
            len += decryptedArrays[i].length;
        }
        // copy to new array
        byte[] decrypted = new byte[len];
        int pos = 0;
        for (byte[] decryptedArray : decryptedArrays) {
            System.arraycopy(decryptedArray, 0, decrypted, pos, decryptedArray.length);
            pos += decryptedArray.length;
        }
        byte[] uncompressed = ConversionHelper.Gzip.decompress(decrypted);
        if (uncompressed == null) {
            return false;
        }
        C2CFriendlyByteBuf buf = wrapByteBuf(Unpooled.wrappedBuffer(uncompressed), sender, senderUUID);
        if (buf == null) {
            return false;
        }
        C2CPacket packet;
        try {
            packet = (C2CPacket) C2C.codec().decode(buf);
        } catch (Throwable e) {
            LOGGER.error("Error decoding C2C packet", e);
            return false;
        }
        if (buf.readableBytes() > 0) {
            LOGGER.error("Found extra bytes while reading C2C packet {}", packet.type());
            return false;
        }
        if (!packet.sender().equals(sender)) {
            LOGGER.error("Detected mismatching packet sender. Expected {}, got {}", sender, packet.sender());
            return false;
        }
        ListenCommand.onPacket(packet, ListenCommand.PacketFlow.C2C_INBOUND);
        try {
            packet.handle(C2CPacketHandler.getInstance());
        } catch (Throwable e) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.nullToEmpty(e.getMessage()));
            LOGGER.error("Error handling C2C packet", e);
        }
        return true;
    }

    @Override
    public void onMessageC2CPacket(MessageC2CPacket packet) {
        String sender = packet.sender();
        String message = packet.message();
        Component formattedComponent;
        try {
            formattedComponent = ExtendedMarkdownArgument.extendedMarkdown().parse(new StringReader(message));
        } catch (CommandSyntaxException e) {
            formattedComponent = Component.nullToEmpty(message);
        }
        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal("/cwe").withStyle(ChatFormatting.AQUA));
        prefix.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal(" "));
        Component component = prefix.append(Component.translatable("c2cpacket.messageC2CPacket.incoming", sender, formattedComponent));
        Minecraft.getInstance().gui.getChat().addMessage(component);
    }

    @Override
    public void onStartTwoPlayerGameC2CPacket(StartTwoPlayerGameC2CPacket packet) {
        TwoPlayerGame.onStartTwoPlayerGame(packet);
    }

    @Override
    public void onPutTicTacToeMarkC2CPacket(PutTicTacToeMarkC2CPacket packet) {
        TicTacToeCommand.onPutTicTacToeMarkC2CPacket(packet);
    }

    @Override
    public void onPutConnectFourPieceC2CPacket(PutConnectFourPieceC2CPacket packet) {
        ConnectFourCommand.onPutConnectFourPieceC2CPacket(packet);
    }

    public static @Nullable C2CFriendlyByteBuf wrapByteBuf(ByteBuf buf, String sender, UUID senderUUID) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        return new C2CFriendlyByteBuf(buf, connection.registryAccess(), sender, senderUUID);
    }

    @Override
    public @NotNull ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
    }

    @Override
    public boolean isAcceptingMessages() {
        return true;
    }
}
