package com.utsusynth.utsu.model.pitch.portamento;

/** Represents a straight portamento. */
class LinearPortamento extends Portamento {
	private final double x1;
	private final double y1;
	private final double x2;
	private final double y2;
	private final double slope;

	LinearPortamento(double x1, double y1, double x2, double y2) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.slope = (y2 - y1) / (x2 - x1);
	}

	@Override
	public double apply(int positionMs) {
		if (positionMs < x1 || positionMs > x2) {
			// TODO: Handle this.
			System.out.println("Tried to apply a linear portamento that doesn't exist here.");
			return 0.0;
		}
		double adjustedX = positionMs - x1;
		return slope * adjustedX + y1;
	}

	@Override
	public double getStartPitch() {
		return y1;
	}

	@Override
	public double getEndPitch() {
		return y2;
	}

}