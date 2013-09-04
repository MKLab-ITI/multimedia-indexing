package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import gr.iti.mklab.visual.utilities.Normalization;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.extract.FeatureExtractor;
import boofcv.abst.feature.orientation.OrientationIntegral;
import boofcv.alg.feature.describe.DescribePointSurf;
import boofcv.alg.feature.detect.interest.FastHessianFeatureDetector;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.factory.feature.describe.FactoryDescribePointAlgs;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.orientation.FactoryOrientationAlgs;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSingleBand;

/**
 * This class implements SURF feature extraction using the BoofCV library.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */

public class SURFExtractor implements DescriptorExtractor {
	/**
	 * The type of SURF to extract. If true the slower-more stable method is used instead of the faster-less
	 * stable.
	 */
	private boolean modified;
	/**
	 * The maximum features extracted per scale.
	 */
	private int maxFeaturesPerScale;
	/**
	 * The minimum intensity threshold.
	 */
	private int minFeatureIntensity;

	private boolean powerNormalization = false;

	public void setPowerNormalization(boolean powerNormalization) {
		this.powerNormalization = powerNormalization;
	}

	private boolean l2Normalization = false;

	public void setL2Normalization(boolean l2Normalization) {
		this.l2Normalization = l2Normalization;
	}

	private int totalDetectionTime;
	private int totalDescriptionTime;
	private int totalNumberInterestPoints;

	public SURFExtractor() throws Exception {
		this(-1, 1, true);
	}

	public SURFExtractor(int maxFeaturesPerScale, int minFeatureIntensity, boolean modified) throws Exception {
		this.maxFeaturesPerScale = maxFeaturesPerScale;
		this.minFeatureIntensity = minFeatureIntensity;
		this.modified = modified;
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points.
	 * 
	 * @throws Exception
	 */
	public double[][] extractDescriptors(BufferedImage image) throws Exception {
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);
		ConvertBufferedImage.convertFrom(image, boofcvImage);
		return easy(boofcvImage);
	}

	/**
	 * Use generalized interfaces for working with SURF. Removed much of the drudgery, but also reduces your
	 * ability to customize your code.
	 * 
	 * @param image
	 *            Input image type. DOES NOT NEED TO BE ImageFloat32, ImageUInt8 works too
	 */
	private double[][] easy(ImageFloat32 image) {
		long start = System.currentTimeMillis();
		// create the detector and descriptors
		DetectDescribePoint<ImageFloat32, SurfFeature> surf = FactoryDetectDescribe.surf(minFeatureIntensity, 2, maxFeaturesPerScale, 2, 9, 4, 4, modified, ImageFloat32.class);
		// DetectDescribePoint<ImageFloat32, SurfFeature> surf =
		// FactoryDetectDescribeNormalization.surf(minFeatureIntensity, 2, maxFeaturesPerScale, 2, 9, 4, 4,
		// modified, ImageFloat32.class);
		// specify the image to process
		surf.detect(image);
		int numPoints = surf.getNumberOfFeatures();
		double[][] descriptions = new double[numPoints][SURFLength];
		for (int i = 0; i < numPoints; i++) {
			descriptions[i] = surf.getDescriptor(i).getValue();
			if (powerNormalization) {
				descriptions[i] = Normalization.normalizePower(descriptions[i], 0.5);
			}
			if (l2Normalization) {
				descriptions[i] = Normalization.normalizeL2(descriptions[i]);
			}
		}

		totalDescriptionTime += (System.currentTimeMillis() - start);
		totalNumberInterestPoints += numPoints;
		// System.out.println("Found Features: " + surf.getNumberOfFeatures());
		// System.out.println("First descriptor's first value: " + surf.getDescriptor(0).value[0]);
		return descriptions;
	}

	/**
	 * Configured exactly the same as the easy example above, but require a lot more code and a more in depth
	 * understanding of how SURF works and is configured.
	 * 
	 * @param image
	 *            Input image type. DOES NOT NEED TO BE ImageFloat32, ImageUInt8 works too
	 */
	@SuppressWarnings({ "unused", "rawtypes" })
	private <II extends ImageSingleBand> void harder(ImageFloat32 image) {
		// SURF works off of integral images
		Class<II> integralType = GIntegralImageOps.getIntegralType(ImageFloat32.class);

		// define the feature detection algorithm
		FeatureExtractor extractor = FactoryFeatureExtractor.nonmax(2, 0, 5, true);
		FastHessianFeatureDetector<II> detector = new FastHessianFeatureDetector<II>(extractor, 200, 2, 9, 4, 4);

		// estimate orientation
		OrientationIntegral<II> orientation = FactoryOrientationAlgs.sliding_ii(0.65, Math.PI / 3.0, 8, -1, 6, integralType);

		DescribePointSurf<II> descriptor = FactoryDescribePointAlgs.<II> msurf(integralType);

		// compute the integral image of 'image'
		II integral = GeneralizedImageOps.createSingleBand(integralType, image.width, image.height);
		GIntegralImageOps.transform(image, integral);

		// detect fast hessian features
		detector.detect(integral);
		// tell algorithms which image to process
		orientation.setImage(integral);
		descriptor.setImage(integral);

		List<ScalePoint> points = detector.getFoundPoints();

		List<SurfFeature> descriptions = new ArrayList<SurfFeature>();

		for (ScalePoint p : points) {
			// estimate orientation
			orientation.setScale(p.scale);
			double angle = orientation.compute(p.x, p.y);

			// extract the SURF description for this region
			SurfFeature desc = descriptor.createDescription();
			descriptor.describe(p.x, p.y, angle, p.scale, desc);

			// save everything for processing later on
			descriptions.add(desc);
		}

		System.out.println("Found Features: " + points.size());
		System.out.println("First descriptor's first value: " + descriptions.get(0).value[0]);
	}

	public int getTotalDetectionTime() {
		return totalDetectionTime;
	}

	public int getTotalDescriptionTime() {
		return totalDescriptionTime;
	}

	public int getTotalNumberInterestPoints() {
		return totalNumberInterestPoints;
	}
}
