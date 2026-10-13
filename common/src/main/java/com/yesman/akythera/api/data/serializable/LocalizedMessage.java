package com.yesman.akythera.api.data.serializable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/// A text shown to players: a lang key resolved through the selected language, or a handwritten text
/// shown as written.
///
/// Encoded as a plain string when handwritten and as `{"translate": "<key>"}` when translatable,
/// the way vanilla text components are.
@ScriptType(autoRegister = false)
public final class LocalizedMessage {
    private static final Codec<String> TRANSLATE_CODEC = Codec.STRING.fieldOf("translate").codec();

    public static final Codec<LocalizedMessage> CODEC = Codec.either(Codec.STRING, TRANSLATE_CODEC).xmap(
        either -> either.map(LocalizedMessage::handwritten, LocalizedMessage::translatable),
        message -> message.translatable ? Either.right(message.text) : Either.left(message.text)
    );

    public static final LocalizedMessage EMPTY = handwritten("");

    /// The lang key when [#translatable], else the text itself.
    private final String text;
    private final boolean translatable;
    private final Component component;

    private LocalizedMessage(String text, boolean translatable) {
        this.text = text;
        this.translatable = translatable;
        this.component = translatable ? Component.translatable(text) : Component.literal(text);
    }

    public static LocalizedMessage translatable(String key) {
        return new LocalizedMessage(key, true);
    }

    public static LocalizedMessage handwritten(String text) {
        return new LocalizedMessage(text, false);
    }

    public String text() {
        return text;
    }

    public boolean isTranslatable() {
        return translatable;
    }

    public Component toComponent() {
        return component;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalizedMessage other && translatable == other.translatable && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, translatable);
    }

    @Override
    public String toString() {
        return translatable ? "translate:" + text : text;
    }
}
