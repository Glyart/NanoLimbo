package ru.nanit.limbo.protocol.packets.play;

import ru.nanit.limbo.protocol.ByteMessage;
import ru.nanit.limbo.protocol.PacketOut;

public class PacketSendResourcePack implements PacketOut {

    private String url;
    private String hash;
    private boolean forced;
    private String message;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public void encode(ByteMessage msg) {
        msg.writeString(url);
        msg.writeString(hash);
        msg.writeBoolean(forced);
        msg.writeString(message);
    }

}
