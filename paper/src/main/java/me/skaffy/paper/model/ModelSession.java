package me.skaffy.paper.model;

import java.util.List;

import me.skaffy.api.model.CustomEntityModel;

public interface ModelSession {
	void setModels(List<CustomEntityModel> models);

	int modelNumber(CustomEntityModel model);

	CustomEntityModel model(int number);

	void sendModelDefinition(byte[] data);
}
