package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;

/**
 * Abstract class for all feature extractors.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public abstract class FeatureExtractor {

	public static final int SIFTLength = 128;
	public static final int SURFLength = 64;

	/**
	 * Whether to apply L2 normalization or not.
	 */
	protected boolean l2Normalization = false;
	/**
	 * Whether to apply power normalization or not.
	 */
	protected boolean powerNormalization = false;
	/**
	 * The total feature extraction time.
	 */
	private long totalExtractionTime;
	/**
	 * The total number of detected interest points.
	 */
	protected long totalNumberInterestPoints;

	public void setPowerNormalization(boolean powerNormalization) {
		this.powerNormalization = powerNormalization;
	}

	public void setL2Normalization(boolean l2Normalization) {
		this.l2Normalization = l2Normalization;
	}

	public double[][] extractFeatures(BufferedImage image) throws Exception {
		long start = System.currentTimeMillis();
		double[][] features = extractFeaturesInternal(image);
		totalNumberInterestPoints += features.length;
		totalExtractionTime += System.currentTimeMillis() - start;
		return features;
	}

	protected abstract double[][] extractFeaturesInternal(BufferedImage image) throws Exception;

	public long getTotalExtractionTime() {
		return totalExtractionTime;
	}

	public long getTotalNumberInterestPoints() {
		return totalNumberInterestPoints;
	}

}
