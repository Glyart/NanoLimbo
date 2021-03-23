package ru.nanit.limbo.protocol.packets.play;

import ru.nanit.limbo.protocol.ByteMessage;
import ru.nanit.limbo.protocol.Packet;

public class PacketResourcePackStatus implements Packet {

    private int status;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public void encode(ByteMessage msg) {
        msg.writeVarInt(status);
    }

    @Override
    public void decode(ByteMessage msg) {
        this.status = msg.readVarInt();
    }

}
