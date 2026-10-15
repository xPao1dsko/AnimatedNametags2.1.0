package me.animatednametags;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Envia al cliente el paquete "SetPassengers" para que el nametag aparezca MONTADO en el jugador
 * sin que el servidor lo considere un pasajero real (asi no bloquea teleports de otros plugins).
 *
 * Usa reflexion sobre las clases de Minecraft (Paper, nombres Mojang) y no necesita ProtocolLib
 * ni PacketEvents. Si algo no esta disponible en tu version, {@link #init} devuelve false y el
 * plugin usa el modo de seguimiento (el nametag sigue al jugador sin montarse).
 */
final class PacketMount {

    private static boolean available;
    private static Constructor<?> bufferConstructor;
    private static Method writeVarInt;
    private static Constructor<?> packetConstructor;
    private static Field connectionField;
    private static Method sendMethod;
    private static Method getHandle;
    private static Method nettyBuffer;

    private PacketMount() {
    }

    static boolean isAvailable() {
        return available;
    }

    static boolean init(Logger logger) {
        available = false;
        try {
            Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf");
            nettyBuffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer");
            Class<?> friendlyBuf = Class.forName("net.minecraft.network.FriendlyByteBuf");
            bufferConstructor = friendlyBuf.getConstructor(byteBuf);
            writeVarInt = friendlyBuf.getMethod("writeVarInt", int.class);

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
            packetConstructor = packetClass.getDeclaredConstructor(friendlyBuf);
            packetConstructor.setAccessible(true);

            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionField = serverPlayer.getField("connection");
            Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> basePacket = Class.forName("net.minecraft.network.protocol.Packet");
            sendMethod = listener.getMethod("send", basePacket);
            getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");

            build(1, new int[]{2}); // autoprueba: si falla, cae al modo follow
            available = true;
            logger.info("Montaje por paquetes ACTIVADO: los nametags van montados en el jugador sin bloquear teleports.");
        } catch (Throwable t) {
            logger.warning("Montaje por paquetes no disponible en esta version (" + t + "). "
                    + "Se usa el modo de seguimiento.");
        }
        return available;
    }

    private static Object build(int vehicleId, int[] passengerIds) throws ReflectiveOperationException {
        Object buffer = bufferConstructor.newInstance(nettyBuffer.invoke(null));
        writeVarInt.invoke(buffer, vehicleId);
        writeVarInt.invoke(buffer, passengerIds.length);
        for (int id : passengerIds) {
            writeVarInt.invoke(buffer, id);
        }
        return packetConstructor.newInstance(buffer);
    }

    /** Hace que, para este espectador, los pasajeros de {@code vehicleId} sean exactamente {@code passengerIds}. */
    static void send(Player viewer, int vehicleId, int[] passengerIds) throws ReflectiveOperationException {
        Object handle = getHandle.invoke(viewer);
        Object connection = connectionField.get(handle);
        sendMethod.invoke(connection, build(vehicleId, passengerIds));
    }
}
