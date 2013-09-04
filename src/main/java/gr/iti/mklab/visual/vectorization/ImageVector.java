package gr.iti.mklab.visual.vectorization;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import gr.iti.mklab.visual.aggregation.VladAggregator;
import gr.iti.mklab.visual.dimreduction.PrincipalComponentAnalysis;
import gr.iti.mklab.visual.extraction.DescriptorExtractor;
import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

/**
 * This class is used to convert an image into a (PCA-reduced) SURF+VLAD vector.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVector implements Callable<ImageVector> {

	/**
	 * The name of the image.
	 */
	private String imageName;

	/**
	 * The folder where the image resists.
	 */
	private String imageFodler;

	/**
	 * The image as a BufferedImage object.
	 */
	private BufferedImage image;

	/**
	 * The image as a vector.
	 */
	private double[] imageVector;

	public double[] getImageVector() {
		return imageVector;
	}

	/**
	 * This object is used for descriptor extraction.
	 */
	private static DescriptorExtractor extractor;

	public static void setExtractor(DescriptorExtractor extractor) {
		ImageVector.extractor = extractor;
	}

	/**
	 * This object is used for VLAD computation.
	 */
	private static VladAggregator VLAD;

	/**
	 * This object is used for PCA projection.
	 */
	private static PrincipalComponentAnalysis PCA;

	private int vectorLength;

	public static void setVLAD(VladAggregator vlad) {
		VLAD = vlad;
	}

	public static void setPCA(PrincipalComponentAnalysis pca) {
		PCA = pca;
	}

	public String getImageName() {
		return imageName;
	}

	public ImageVector(String imageFolder, String imageName, int vectorLength) {
		this.imageFodler = imageFolder;
		this.imageName = imageName;
		this.vectorLength = vectorLength;
		this.image = null;
	}

	public ImageVector(String imageName, BufferedImage image, int vectorLength) {
		this.imageFodler = null;
		this.imageName = imageName;
		this.image = image;
		this.vectorLength = vectorLength;
	}

	/**
	 * Transforms the image into a vector
	 * 
	 * @throws Exception
	 */
	public void transformToVector() throws Exception {
		if (vectorLength > VLAD.getVectorLength() || vectorLength <= 0) {
			throw new Exception("Vector length should be between 1 and " + VLAD.getVectorLength());
		}
		// First the image is read if the image field is null
		if (image == null) {
			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFodler + imageName));
			} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
				// This exception is probably because of a grayscale jpeg image.
				System.out.println("Exception: " + e.getMessage() + " | Image: " + imageName);
				image = ImageIOGreyScale.read(new File(imageFodler + imageName));
			}
		}
		// next the image is scaled
		ImageScaling scale = new ImageScaling(512 * 384);
		image = scale.maxPixelsScaling(image);

		// next the descriptors are extracted
		double[][] descriptors = extractor.extractDescriptors(image);
		// System.out.println("Num points: " + descriptors.length);

		// next the descriptors are aggregated
		double[] vladVector = VLAD.aggregate(descriptors);

		// print the VLAD vector
		// System.out.println("VLAD vector:\n" + Arrays.toString(vladVector));

		if (vladVector.length == vectorLength) {
			this.imageVector = vladVector;
			return;
		}

		// next the vector is pca-reduded
		double[] transformed = PCA.sampleToEigenSpace(vladVector);

		// print the PCA transformed VLAD vector
		// System.out.println("PCA transformed VLAD vector:\n" + Arrays.toString(transformed));

		this.imageVector = transformed;
		return;
	}

	@Override
	public ImageVector call() throws Exception {
		transformToVector();
		return this;
	}
}
