package ru.nanit.limbo.protocol.packets.play;

import ru.nanit.limbo.protocol.ByteMessage;
import ru.nanit.limbo.protocol.PacketOut;

public class PacketSendResourcePack implements PacketOut {

    private String url;
    private String hash;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public void encode(ByteMessage msg) {
        msg.writeString(url);
        msg.writeString(hash);
    }

}
