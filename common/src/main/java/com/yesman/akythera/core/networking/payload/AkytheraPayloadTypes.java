package com.yesman.akythera.core.networking.payload;

import com.yesman.akythera.client.core.networking.AkytheraBiDirectionalPayloadHandler;
import com.yesman.akythera.client.core.networking.AkytheraClientBoundPayloadHandler;
import com.yesman.akythera.core.networking.AkytheraServerBoundPayloadHandler;
import com.yesman.akythera.core.networking.payload.bidirectional.BiDirectionalReplaceAnimation;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundDataBlockSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;

import java.util.ArrayList;
import java.util.List;

/// Manager class for payload types pack a packet info as (type, codec, handler)
///
/// Called on each platform to register payload channel
public final class AkytheraPayloadTypes {
	public static final List<PayloadChannel<?>> CLIENT_BOUND = new ArrayList<>();
	public static final List<PayloadChannel<?>> SERVER_BOUND = new ArrayList<>();
	public static final List<PayloadChannel<?>> BI_DIRECTIONAL = new ArrayList<>();

	static {
		// client bound packets
		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundPlayAnimMontage.TYPE, ClientBoundPlayAnimMontage.STREAM_CODEC, AkytheraClientBoundPayloadHandler::handlePlayMontage));
		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundDataBlockSync.TYPE, ClientBoundDataBlockSync.STREAM_CODEC, AkytheraClientBoundPayloadHandler::handleBlockSync));

		// bi-directional packets
		BI_DIRECTIONAL.add(new PayloadChannel<>(BiDirectionalReplaceAnimation.TYPE, BiDirectionalReplaceAnimation.STREAM_CODEC, AkytheraBiDirectionalPayloadHandler::handleReplaceAnimation));
	}
}
