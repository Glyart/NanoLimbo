package ru.nanit.limbo.world;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import ru.nanit.limbo.server.LimboServer;

import java.io.IOException;
import java.io.InputStream;

public final class DimensionRegistry {

    private CompoundBinaryTag defaultDimension;
    private CompoundBinaryTag codec;

    public CompoundBinaryTag getCodec(){
        return codec;
    }

    public CompoundBinaryTag getDefaultDimension() {
        return defaultDimension;
    }

    public void load(LimboServer server, String def) throws IOException {
        InputStream in = server.getClass().getResourceAsStream("/dimension_registry.nbt");
        codec = BinaryTagIO.readCompressedInputStream(in);
        ListBinaryTag dimensions = codec.getCompound("minecraft:dimension_type").getList("value");

        defaultDimension = (CompoundBinaryTag) ((CompoundBinaryTag) dimensions.get(0)).get("element");
    }
}
