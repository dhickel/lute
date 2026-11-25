package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import io.mindspice.lute.engine.data.json.ObjectData;

public record Pickup(ObjectData.Pickup rule, String weapon) implements Component { }
