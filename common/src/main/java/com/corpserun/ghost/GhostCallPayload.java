package com.corpserun.ghost;

import com.corpserun.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a ghost left-clicked, asking to play its call sound. Carries no data. */
public record GhostCallPayload() implements CustomPacketPayload {

    public static final GhostCallPayload INSTANCE = new GhostCallPayload();
    public static final CustomPacketPayload.Type<GhostCallPayload> TYPE = new CustomPacketPayload.Type<>(Constants.id("ghost_call"));
    public static final StreamCodec<ByteBuf, GhostCallPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
