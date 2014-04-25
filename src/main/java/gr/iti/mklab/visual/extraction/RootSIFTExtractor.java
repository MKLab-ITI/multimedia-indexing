package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.Normalization;

import java.awt.image.BufferedImage;

/**
 * This class uses the BoofCV library for extracting RootSIFT features.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class RootSIFTExtractor extends SIFTExtractor {

	/**
	 * Constructor using default "good" settings for the detector.
	 * 
	 * @throws Exception
	 */
	public RootSIFTExtractor() {
		super(-1, 1);
	}

	public RootSIFTExtractor(int maxFeaturesPerScale, float detectThreshold) {
		super(maxFeaturesPerScale, detectThreshold);
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points.
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {
		double[][] features = super.extractFeaturesInternal(image);
		for (int i = 0; i < features.length; i++) {
			Normalization.normalizePower(features[i], 0.5);
			Normalization.normalizeL2(features[i]);
		}
		return features;
	}

}
