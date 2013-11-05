package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.Normalization;

import java.awt.image.BufferedImage;

import boofcv.abst.feature.describe.ConfigSiftScaleSpace;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

/**
 * This class used the BoofCV library for extracting SIFT features.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class SIFTExtractor extends AbstractFeatureExtractor {

	public SIFTExtractor() {
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points. <br>
	 * TO DO: remove code in comments that is used for earlier versions of BoofCV<br>
	 * TO DO: and more details about the extraction parameters
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);
		ConvertBufferedImage.convertFrom(image, boofcvImage);
		// == journal version ==
		// DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribeNormalization.sift(4, 1,
		// doubleInputImaged, -1);
		// == v0.12 version ==
		// DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribe.sift(4, 1, false, -1);
		// == v0.14++ version ==
		ConfigSiftScaleSpace conf = new ConfigSiftScaleSpace();
		DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribe.sift(conf, null, null,
				null);

		// specify the image to process
		sift.detect(boofcvImage);
		int numPoints = sift.getNumberOfFeatures();
		double[][] descriptions = new double[numPoints][SIFTLength];
		for (int i = 0; i < numPoints; i++) {
			descriptions[i] = sift.getDescription(i).getValue();
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
