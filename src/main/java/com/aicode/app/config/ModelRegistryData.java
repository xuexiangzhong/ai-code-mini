package com.aicode.app.config;

import java.util.ArrayList;
import java.util.List;

/** JSON persistence shape for {@link ModelRegistry}. */
public final class ModelRegistryData {
    public String defaultModelId = "";
    public List<ModelProfile> models = new ArrayList<>();
}
