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
 * This class implements SURF feature extraction using the BoofCV library.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class SURFExtractor extends AbstractFeatureExtractor {
	/**
	 * The maximum features extracted per scale.
	 */
	private int maxFeaturesPerScale;
	/**
	 * The minimum intensity threshold.
	 */
	private int minFeatureIntensity;

	public SURFExtractor() throws Exception {
		this(-1, 1);
	}

	public SURFExtractor(int maxFeaturesPerScale, int minFeatureIntensity) throws Exception {
		this.maxFeaturesPerScale = maxFeaturesPerScale;
		this.minFeatureIntensity = minFeatureIntensity;
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points. <br>
	 * TO DO: remove code in comments that is used for earlier versions of BoofCV<br>
	 * TO DO: and more details about the extraction parameters
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);
		ConvertBufferedImage.convertFrom(image, boofcvImage);

		// create the detector and descriptor
		// == journal version ==
		// DetectDescribePoint<ImageFloat32, SurfFeature> surf =
		// FactoryDetectDescribeNormalization.surf(minFeatureIntensity, 2, maxFeaturesPerScale, 2, 9, 4, 4,
		// modified, ImageFloat32.class);
		// == v0.12 version ==
		// DetectDescribePoint<ImageFloat32, SurfFeature> surf =
		// FactoryDetectDescribe.surf(minFeatureIntensity, 2, maxFeaturesPerScale, 2, 9, 4, 4,
		// modified, ImageFloat32.class);
		// == v0.14 version or later ==
		ConfigFastHessian conf = new ConfigFastHessian(minFeatureIntensity, 2, maxFeaturesPerScale, 2, 9, 4,
				4);
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
