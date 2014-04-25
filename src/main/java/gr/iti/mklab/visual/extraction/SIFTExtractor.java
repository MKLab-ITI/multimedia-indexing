package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

/**
 * This class uses the BoofCV library for extracting SIFT features.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class SIFTExtractor extends AbstractFeatureExtractor {

	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigSiftDetector#maxFeaturesPerScale}
	 */
	protected int maxFeaturesPerScale;

	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigSiftDetector#detectThreshold}
	 */
	protected float detectThreshold;

	/**
	 * Constructor using default "good" settings for the detector.
	 * 
	 * @throws Exception
	 */
	public SIFTExtractor() {
		this(-1, 1);
	}

	public SIFTExtractor(int maxFeaturesPerScale, float detectThreshold) {
		this.maxFeaturesPerScale = maxFeaturesPerScale;
		this.detectThreshold = detectThreshold;
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points.
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);
		// create the SIFT detector and descriptor in BoofCV v0.15
		ConfigSiftDetector conf = new ConfigSiftDetector(2, detectThreshold, maxFeaturesPerScale, 5);
		DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribe.sift(null, conf, null,
				null);

		// specify the image to process
		sift.detect(boofcvImage);
		int numPoints = sift.getNumberOfFeatures();
		double[][] descriptions = new double[numPoints][SIFTLength];
		for (int i = 0; i < numPoints; i++) {
			descriptions[i] = sift.getDescription(i).getValue();
		}
		return descriptions;
	}
}
