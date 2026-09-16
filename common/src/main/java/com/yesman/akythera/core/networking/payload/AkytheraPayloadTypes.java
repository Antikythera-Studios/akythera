package com.yesman.akythera.core.networking.payload;

import com.yesman.akythera.client.core.networking.AkytheraBiDirectionalPayloadHandler;
import com.yesman.akythera.client.core.networking.AkytheraClientBoundPayloadHandler;
import com.yesman.akythera.core.networking.AkytheraServerBoundPayloadHandler;
import com.yesman.akythera.core.networking.payload.bidirectional.BiDirectionalReplaceAnimation;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEffectSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEquipmentSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityLifecycle;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityStateSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityTagSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundCancelAbilityCue;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundDataBlockSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAbilityCue;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;
import com.yesman.akythera.core.networking.payload.serverbound.ServerBoundAbilityTrigger;

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
		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundPlayAnimMontage.TYPE, ClientBoundPlayAnimMontage.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handlePlayMontage));
		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundDataBlockSync.TYPE, ClientBoundDataBlockSync.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleBlockSync));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundAbilityTagSync.TYPE, ClientBoundAbilityTagSync.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleAbilityTagSync));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundAbilityEquipmentSync.TYPE, ClientBoundAbilityEquipmentSync.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleAbilityEquipmentSync));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundAbilityLifecycle.TYPE, ClientBoundAbilityLifecycle.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleAbilityLifecycle));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundAbilityStateSync.TYPE, ClientBoundAbilityStateSync.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleAbilityStateSync));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundAbilityEffectSync.TYPE, ClientBoundAbilityEffectSync.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleAbilityEffectSync));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundPlayAbilityCue.TYPE, ClientBoundPlayAbilityCue.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handlePlayAbilityCue));

		CLIENT_BOUND.add(new PayloadChannel<>(ClientBoundCancelAbilityCue.TYPE, ClientBoundCancelAbilityCue.STREAM_CODEC, () -> AkytheraClientBoundPayloadHandler::handleCancelAbilityCue));

		// server bound packets
		SERVER_BOUND.add(new PayloadChannel<>(ServerBoundAbilityTrigger.TYPE, ServerBoundAbilityTrigger.STREAM_CODEC, () -> AkytheraServerBoundPayloadHandler::handleAbilityTrigger));

		// bi-directional packets
		BI_DIRECTIONAL.add(new PayloadChannel<>(BiDirectionalReplaceAnimation.TYPE, BiDirectionalReplaceAnimation.STREAM_CODEC, () -> AkytheraBiDirectionalPayloadHandler::handleReplaceAnimation));
	}
}
