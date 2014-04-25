package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;

/**
 * Abstract class for all feature extractors.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public abstract class AbstractFeatureExtractor {

	public static final int SURFLength = 64;
	public static final int SIFTLength = 128;
	public static final int CololSURFLength = 3 * SURFLength;

	/**
	 * The total feature extraction time.
	 */
	protected long totalExtractionTime;
	/**
	 * The total number of detected interest points.
	 */
	protected long totalNumberInterestPoints;

	/**
	 * Any normalizations of the features should be performed in the specific classes!
	 * 
	 * @param image
	 * @return
	 * @throws Exception
	 */
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
