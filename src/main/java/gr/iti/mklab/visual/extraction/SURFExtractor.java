package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.Normalization;

import java.awt.image.BufferedImage;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

/**
 * This class uses the BoofCV library for extracting SURF features.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class SURFExtractor extends AbstractFeatureExtractor {
	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigFastHessian#maxFeaturesPerScale}
	 */
	private int maxFeaturesPerScale;
	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigFastHessian#detectThreshold}
	 */
	private int detectThreshold;

	/**
	 * Constructor using default "good" settings for the detector.
	 * 
	 * @throws Exception
	 */
	public SURFExtractor() throws Exception {
		this(-1, 1);
	}

	public SURFExtractor(int maxFeaturesPerScale, int minFeatureIntensity) throws Exception {
		this.maxFeaturesPerScale = maxFeaturesPerScale;
		this.detectThreshold = minFeatureIntensity;
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points.
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);

		// create the SURF detector and descriptor in BoofCV v0.15
		ConfigFastHessian conf = new ConfigFastHessian(detectThreshold, 2, maxFeaturesPerScale, 2, 9, 4, 4);
		DetectDescribePoint<ImageFloat32, SurfFeature> surf = FactoryDetectDescribe.surfStable(conf, null,
				null, ImageFloat32.class);

		// specify the image to process
		surf.detect(boofcvImage);
		int numPoints = surf.getNumberOfFeatures();
		double[][] descriptions = new double[numPoints][SURFLength];
		for (int i = 0; i < numPoints; i++) {
			descriptions[i] = surf.getDescription(i).getValue();
			if (powerNormalization) {
				descriptions[i] = Normalization.normalizePower(descriptions[i], 0.5);
			}
			if (l2Normalization) {
				descriptions[i] = Normalization.normalizeL2(descriptions[i]);
			}
		}
		return descriptions;
	}
}
