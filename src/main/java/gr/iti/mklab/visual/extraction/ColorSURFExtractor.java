package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.Normalization;

import java.awt.image.BufferedImage;
import java.util.List;

import boofcv.abst.feature.detect.extract.ConfigExtract;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.orientation.OrientationIntegral;
import boofcv.alg.feature.describe.DescribePointSurf;
import boofcv.alg.feature.detect.interest.FastHessianFeatureDetector;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.core.image.ConvertImage;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.factory.feature.describe.FactoryDescribePointAlgs;
import boofcv.factory.feature.detect.extract.FactoryFeatureExtractor;
import boofcv.factory.feature.orientation.FactoryOrientationAlgs;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSingleBand;
import boofcv.struct.image.MultiSpectral;

/**
 * This class uses the BoofCV library for extracting color-SURF features as described in:<br>
 * <em>E. Spyromitros-Xioufis, S. Papadopoulos, Y. Kompatsiaris, G. Tsoumakas, I. Vlahavas, "A Comprehensive Study over VLAD and Product Quantization in Large-scale Image Retrieval", IEEE Transactions on Multimedia, 2014. (accepted with minor)</em>
 * <br>
 * <br>
 * When the image is greyscale (only 1 band), the descriptor is repeated 3 times.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class ColorSURFExtractor extends AbstractFeatureExtractor {
	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigFastHessian#maxFeaturesPerScale}
	 */
	private int maxFeaturesPerScale;
	/**
	 * Sets the value of {@link boofcv.abst.feature.detect.interest.ConfigFastHessian#detectThreshold}
	 */
	private int detectThreshold;
	/**
	 * Whether each band should be normalized separately before the final normalization (false by default).
	 */
	private boolean perBandNormalization = false;

	/**
	 * Constructor using default "good" settings for the detector.
	 * 
	 * @throws Exception
	 */
	public ColorSURFExtractor() throws Exception {
		this(-1, 1);
	}

	public ColorSURFExtractor(int maxFeaturesPerScale, int minFeatureIntensity) throws Exception {
		this.maxFeaturesPerScale = maxFeaturesPerScale;
		this.detectThreshold = minFeatureIntensity;
	}

	/**
	 * Detects key points inside the image and computes descriptions at those points. <br>
	 */
	protected double[][] extractFeaturesInternal(BufferedImage image) {

		double[][] descriptions = harder(image);
		if (!perBandNormalization) {
			// if perBandNormalization is off then do a final L2 normalization
			for (int i = 0; i < descriptions.length; i++) {
				Normalization.normalizeL2(descriptions[i]);
				// SurfDescribeOps.normalizeFeatures(descriptions[i]);
			}
		}
		return descriptions;
	}

	public <II extends ImageSingleBand> double[][] harder(BufferedImage image) {
		MultiSpectral<ImageFloat32> colorImage = ConvertBufferedImage.convertFromMulti(image, null, true,
				ImageFloat32.class);
		// convert the color image to greyscale
		ImageFloat32 greyscaleImage = ConvertImage.average((MultiSpectral<ImageFloat32>) colorImage, null);

		// SURF works off of integral images
		Class<II> integralType = GIntegralImageOps.getIntegralType(ImageFloat32.class);

		// define the feature detection algorithm
		NonMaxSuppression extractor = FactoryFeatureExtractor.nonmax(new ConfigExtract(2, detectThreshold, 5,
				true));
		FastHessianFeatureDetector<II> detector = new FastHessianFeatureDetector<II>(extractor,
				maxFeaturesPerScale, 2, 9, 4, 4);

		// estimate orientation
		OrientationIntegral<II> orientation = FactoryOrientationAlgs.sliding_ii(null, integralType);

		DescribePointSurf<II> descriptor = FactoryDescribePointAlgs.<II> surfStability(null, integralType);

		// compute the integral image of the greyscale 'image'
		II integralgrey = GeneralizedImageOps.createSingleBand(integralType, greyscaleImage.width,
				greyscaleImage.height);
		GIntegralImageOps.transform(greyscaleImage, integralgrey);

		// detect fast hessian features
		detector.detect(integralgrey);

		// === This is the point were the code starts deviating from the standard SURF! ===
		// tell algorithms which image to process
		orientation.setImage(integralgrey);

		List<ScalePoint> points = detector.getFoundPoints();
		double[][] descriptions = new double[points.size()][3 * descriptor.getDescriptionLength()];

		double[] angles = new double[points.size()];
		int l = 0;
		for (ScalePoint p : points) {
			orientation.setScale(p.scale);
			angles[l] = orientation.compute(p.x, p.y);
			l++;
		}

		for (int i = 0; i < 3; i++) {
			// check if it is actually a greyscale image, take always the 1st band!
			ImageFloat32 colorImageBand = null;
			if (colorImage.getNumBands() == 1) {
				colorImageBand = colorImage.getBand(0);
			} else {
				colorImageBand = colorImage.getBand(i);
			}

			// compute the integral image of the i-th band of the color 'image'
			II integralband = GeneralizedImageOps.createSingleBand(integralType, colorImageBand.width,
					colorImageBand.height);
			GIntegralImageOps.transform(colorImageBand, integralband);

			// tell algorithms which image to process
			// orientation.setImage(integralband);
			descriptor.setImage(integralband);

			int j = 0;
			for (ScalePoint p : points) {
				// estimate orientation
				// orientation.setScale(p.scale);
				// double angle = orientation.compute(p.x, p.y);
				// extract the SURF description for this region
				SurfFeature desc = descriptor.createDescription();
				descriptor.describe(p.x, p.y, angles[j], p.scale, (TupleDesc_F64) desc);
				double[] banddesc = desc.getValue();
				if (perBandNormalization) {
					banddesc = Normalization.normalizeL2(banddesc);
				}
				for (int k = 0; k < SURFLength; k++) {
					descriptions[j][i * SURFLength + k] = banddesc[k];
				}
				j++;
			}
		}

		return descriptions;
	}

	public void setPerBandNormalization(boolean perBandNormalization) {
		this.perBandNormalization = perBandNormalization;
	}
}
