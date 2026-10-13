package com.yesman.akythera.client.api.particle.module;

import com.yesman.akythera.api.data.modularize.node.PureNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares what a built-in module's [PureNode] parameter answers, for tools that author one: a record component of
/// the module, by the name its json field has.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ModuleParameter {
    /// The type the parameter answers. A registered script type.
    Class<?> value();

    /// Names the parameter of the same module this one never exceeds, element by element. Blank for none.
    String atMost() default "";

    /// Names the parameter of the same module this one never falls below, element by element. Blank for none.
    String atLeast() default "";

    /// Whether the parameter is a direction, of which only where it points matters.
    boolean direction() default false;
}
