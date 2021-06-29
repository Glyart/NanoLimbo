package ru.nanit.limbo.protocol.packets.play;

import ru.nanit.limbo.protocol.ByteMessage;
import ru.nanit.limbo.protocol.PacketOut;

public class PacketPlayerPositionAndLook implements PacketOut {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private byte flags = 0x00;
    private int teleportId;

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public void setTeleportId(int teleportId) {
        this.teleportId = teleportId;
    }

    @Override
    public void encode(ByteMessage msg) {
        msg.writeDouble(x);
        msg.writeDouble(y);
        msg.writeDouble(z);
        msg.writeFloat(yaw);
        msg.writeFloat(pitch);
        msg.writeByte(flags);
        msg.writeVarInt(teleportId);
        msg.writeBoolean(false);
    }

}
