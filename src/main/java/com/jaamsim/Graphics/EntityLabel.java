/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2015 Ausenco Engineering Canada Inc.
 * Copyright (C) 2018-2022 JaamSim Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jaamsim.Graphics;

import java.util.ArrayList;

import com.jaamsim.Commands.DefineCommand;
import com.jaamsim.basicsim.Entity;
import com.jaamsim.basicsim.ErrorException;
import com.jaamsim.basicsim.GUIListener;
import com.jaamsim.basicsim.JaamSimModel;
import com.jaamsim.input.EntityInput;
import com.jaamsim.input.Input;
import com.jaamsim.input.InputAgent;
import com.jaamsim.input.InputCallback;
import com.jaamsim.input.Keyword;
import com.jaamsim.input.KeywordIndex;
import com.jaamsim.math.Vec3d;
import com.jaamsim.units.DistanceUnit;

public class EntityLabel extends TextBasics {

	@Keyword(description = "The name of an entity that is labelled by this EntityLabel.",
	         exampleList = {"DisplayEntity1"})
	protected final EntityInput<DisplayEntity> targetEntity;

	{
		desc.setHidden(true);
		attributeDefinitionList.setHidden(true);
		namedExpressionInput.setHidden(true);

		targetEntity = new EntityInput<>(DisplayEntity.class, "TargetEntity", KEY_INPUTS, null);
		targetEntity.setCallback(inputCallback);
		this.addInput(targetEntity);

		textHeight.setCallback(textHeightCallback);
		targetEntity.setHidden(true);
		relativeEntity.setHidden(true);
		regionInput.setHidden(true);
	}

	public EntityLabel() {}

	public static EntityLabel getLabel(DisplayEntity ent) {

		// Is there a label with the correct name?
		Entity label = ent.getJaamSimModel().getNamedEntity(ent.getName() + "_Label");
		if (label == null) // FIXME - remove when all labels have the correct name
			label = ent.getJaamSimModel().getNamedEntity(ent.getName() + "_Label1");
		if (label instanceof EntityLabel)
			return (EntityLabel) label;

		// Otherwise, search for a label with the correct target entity
		// (Required when the entity's name has been changed)
		for (EntityLabel lab : ent.getJaamSimModel().getClonesOfIterator(EntityLabel.class)) {
			if (lab.getTarget() == ent)
				return lab;
		}
		return null;
	}

	@Override
	public void resetGraphics() {}

	static final InputCallback inputCallback = new InputCallback() {
		@Override
		public void callback(Entity ent, Input<?> inp) {
			((EntityLabel)ent).updateInputValue();
		}
	};

	void updateInputValue() {
		DisplayEntity ent = targetEntity.getValue();
		if (ent == null) {
			setText("ERROR");
			return;
		}
		setText(ent.getName());
	}

	static final InputCallback textHeightCallback = new InputCallback() {
		@Override
		public void callback(Entity ent, Input<?> inp) {
			((EntityLabel)ent).resizeForText();
		}
	};

	@Override
	public boolean isGraphicsNominal() {
		return true;
	}

	@Override
	public String getText() {
		if (isEditMode())
			return super.getText();
		Entity ent = targetEntity.getValue();
		if (ent == null)
			return "ERROR";
		return ent.getName();
	}

	@Override
	public void setEditMode(boolean bool) {
		updateForTargetNameChange();
		super.setEditMode(bool);
	}

	@Override
	public void acceptEdits() {
		GUIListener gui = getJaamSimModel().getGUIListener();
		if (gui == null)
			return;
		try {
			// Rename both the target entity and the label
			gui.renameEntity(targetEntity.getValue(), getText());
			super.acceptEdits();
		}
		catch (ErrorException e) {
			super.cancelEdits();
			gui.invokeErrorDialogBox("Input Error", e.getMessage());
		}
	}

	private DisplayEntity getTarget() {
		return targetEntity.getValue();
	}

	public void updateForTargetNameChange() {
		String targetName = targetEntity.getValue().getName();
		setText(targetName);
		this.resizeForText();
	}

	/**
	 * Creates a label for the specified entity.
	 * @param ent - entity to be labeled
	 * @param undo - true if undo is to be enabled
	 * @return label object
	 */
	public static EntityLabel createLabel(DisplayEntity ent, boolean undo) {
		EntityLabel label = getLabel(ent);
		if (label != null)
			return label;

		// Create the EntityLabel object
		JaamSimModel simModel = ent.getJaamSimModel();
		String name = InputAgent.getUniqueName(simModel, ent.getName() + "_Label", "");
		if (undo) {
			InputAgent.storeAndExecute(new DefineCommand(simModel, EntityLabel.class, name));
			label = (EntityLabel)simModel.getNamedEntity(name);
		}
		else {
			label = InputAgent.defineEntityWithUniqueName(simModel, EntityLabel.class, name, "", true);
		}

		// Assign inputs that link the label to its target entity
		InputAgent.applyArgs(label, "TargetEntity", ent.getName());
		InputAgent.applyArgs(label, "RelativeEntity", ent.getName());
		if (ent.getCurrentRegion() != null)
			InputAgent.applyArgs(label, "Region", ent.getCurrentRegion().getName());

		// Set the visible views to match its target entity
		if (ent.getVisibleViews() != null) {
			ArrayList<String> tokens = new ArrayList<>(ent.getVisibleViews().size());
			for (View v : ent.getVisibleViews()) {
				tokens.add(v.getName());
			}
			KeywordIndex kw = new KeywordIndex("VisibleViews", tokens, null);
			InputAgent.apply(label, kw);
		}

		// Set the label's position
		Vec3d pos = label.getDefaultPosition();
		InputAgent.apply(label, simModel.formatVec3dInput("Position", pos, DistanceUnit.class));

		// Set the label's size
		label.resizeForText();

		return label;
	}

	public Vec3d getDefaultPosition() {
		DisplayEntity ent = getTarget();
		double ypos = -0.15d;
		if (!ent.usePointsInput())
			ypos -= 0.5d*ent.getSize().y;
		return new Vec3d(0.0d, ypos, 0.0d);
	}

	public static void showLabel(DisplayEntity ent, boolean bool) {
		EntityLabel label = getLabel(ent);

		// Does the label exist yet?
		if (label == null) {
			if (!bool)
				return;
			label = EntityLabel.createLabel(ent, true);
		}

		// Show or hide the label
		if (label.getShow() == bool)
			return;
		InputAgent.applyBoolean(label, "Show", bool);
	}

	public static void showTemporaryLabel(DisplayEntity ent, boolean bool, boolean undo) {
		EntityLabel label = getLabel(ent);

		// Does the label exist yet?
		if (label == null) {
			if (!bool)
				return;
			label = EntityLabel.createLabel(ent, undo);
			InputAgent.applyBoolean(label, "Show", false);
		}

		// Show or hide the label
		label.setShow(bool || label.getShowInput());
	}

	public static boolean canLabel(DisplayEntity ent) {
		return !(ent instanceof TextEntity) && !(ent instanceof OverlayEntity)
				&& !(ent instanceof Graph) && !(ent instanceof Arrow) && !(ent instanceof Region)
				&& !(ent instanceof Polyline)
				&& ent.isRegistered()
				&& !ent.getName().equals("XY-Grid") && !ent.getName().equals("XYZ-Axis");
	}

	@Override
	public boolean isDefault() {
		if (getTarget() == null)
			return true;
		Vec3d pos = getDefaultPosition();
		return getPosition().near3(pos) && super.isDefault();
	}

	@Override
	public boolean getShow(double simTime) {
		return super.getShow(simTime) && getTarget() != null
				&& getTarget().getShow(simTime) && getTarget().isMovable();
	}

}
