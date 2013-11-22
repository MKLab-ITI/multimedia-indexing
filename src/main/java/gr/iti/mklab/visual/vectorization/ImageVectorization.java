package gr.iti.mklab.visual.vectorization;

import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

/**
 * This class represents an image vectorization task. It implements the Callable interface and can be used for
 * multi-threaded image vectorization.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVectorization implements Callable<ImageVectorizationResult> {

	/**
	 * Image will be scaled at this maximum number of pixels before vectorization.
	 */
	private int maxImageSizeInPixels = 1024 * 768;

	/**
	 * The filename of the image.
	 */
	private String imageFilename;

	/**
	 * The directory (full path) where the image resides.
	 */
	private String imageFolder;

	/**
	 * The image as a BufferedImage object.
	 */
	private BufferedImage image;

	/**
	 * The target length of the extracted vector.
	 */
	private int vectorLength;

	/**
	 * This object is used for descriptor extraction.
	 */
	private static AbstractFeatureExtractor featureExtractor;

	/**
	 * This object is used for extracting VLAD vectors with multiple vocabulary aggregation.
	 */
	private static VladAggregatorMultipleVocabularies vladAggregator;

	/**
	 * This object is used for PCA projection and whitening.
	 */
	private static PCA pcaProjector;

	/**
	 * If set to true, debug output is displayed.
	 */
	public boolean debug = false;

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * This constructor is used when the image should be read into a BufferedImage object from the given
	 * folder.
	 * 
	 * @param imageFolder
	 *            The folder (full path) where the image resides
	 * @param imageFilename
	 *            The filename of the image
	 * @param vectorLength
	 *            The target length of the vector
	 * @param maxImageSizeInPixels
	 *            The maximum image size of in pixels. It the image is larger, it is first scaled down prior
	 *            to vectorization.
	 */
	public ImageVectorization(String imageFolder, String imageFilename, int vectorLength,
			int maxImageSizeInPixels) {
		this.imageFolder = imageFolder;
		this.imageFilename = imageFilename;
		this.vectorLength = vectorLength;
		this.maxImageSizeInPixels = maxImageSizeInPixels;
	}

	/**
	 * This constructor is used when the image has been already read into a BufferedImage object.
	 * 
	 * @param imageFilename
	 *            The filename of the image
	 * @param image
	 *            A BufferedImage object of the image
	 * @param vectorLength
	 *            The target length of the vector
	 * @param maxImageSizeInPixels
	 *            The maximum image size of in pixels. It the image is larger, it is first scaled down prior
	 *            to vectorization.
	 */
	public ImageVectorization(String imageFilename, BufferedImage image, int vectorLength,
			int maxImageSizeInPixels) {
		this.imageFilename = imageFilename;
		this.vectorLength = vectorLength;
		this.image = image;
		this.maxImageSizeInPixels = maxImageSizeInPixels;
	}

	@Override
	/**
	 * Returns an ImageVectorizationResult object from where the image's vector and name can be
	 * obtained.
	 */
	public ImageVectorizationResult call() throws Exception {
		if (debug)
			System.out.println("Vectorization for image " + imageFilename + " started.");
		double[] imageVector = transformToVector();
		if (debug)
			System.out.println("Vectorization for image " + imageFilename + " completed.");
		return new ImageVectorizationResult(imageFilename, imageVector);
	}

	/**
	 * Transforms the image into a vector and returns the result.
	 * 
	 * @return The image's vector.
	 * @throws Exception
	 */
	public double[] transformToVector() throws Exception {
		if (vectorLength > vladAggregator.getVectorLength() || vectorLength <= 0) {
			throw new Exception("Vector length should be between 1 and " + vladAggregator.getVectorLength());
		}
		// first the image is read if the image field is null
		if (image == null) {
			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFolder + imageFilename));
			} catch (IllegalArgumentException e) {
				// this exception is probably thrown because of a greyscale jpeg image
				System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename);
				// retry with the modified class
				image = ImageIOGreyScale.read(new File(imageFolder + imageFilename));
			}
		}
		// next the image is scaled
		ImageScaling scale = new ImageScaling(maxImageSizeInPixels);
		image = scale.maxPixelsScaling(image);

		// next the local features are extracted
		double[][] features = featureExtractor.extractFeatures(image);

		// next the features are aggregated
		double[] vladVector = vladAggregator.aggregate(features);

		if (vladVector.length == vectorLength) {
			// no projection is needed
			return vladVector;
		} else {
			// pca projection is applied
			double[] projected = pcaProjector.sampleToEigenSpace(vladVector);
			return projected;
		}
	}

	/**
	 * Sets the FeatureExtractor object that will be used.
	 * 
	 * @param extractor
	 */
	public static void setFeatureExtractor(AbstractFeatureExtractor extractor) {
		ImageVectorization.featureExtractor = extractor;
	}

	/**
	 * Sets the VladAggregatorMultipleVocabularies object that will be used.
	 * 
	 * @param vladAggregator
	 */
	public static void setVladAggregator(VladAggregatorMultipleVocabularies vladAggregator) {
		ImageVectorization.vladAggregator = vladAggregator;
	}

	/**
	 * Sets the PCA projection object that will be used.
	 * 
	 * @param pcaProjector
	 */
	public static void setPcaProjector(PCA pcaProjector) {
		ImageVectorization.pcaProjector = pcaProjector;
	}

	/**
	 * Example of a single image vectorization using this class.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		String imageFolder = "C:/images/";
		String imagFilename = "test.jpg";
		String[] codebookFiles = { "C:/codebook1.csv", "C:/codebook2.csv", "C:/codebook3.csv",
				"C:/codebook4.csv" };
		int[] numCentroids = { 64, 64, 64, 64 };
		String pcaFilename = "C:/pca.txt";
		int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
		int targetLength = 128;

		ImageVectorization imvec = new ImageVectorization(imageFolder, imagFilename, targetLength, 512 * 384);
		ImageVectorization.setFeatureExtractor(new SURFExtractor());
		ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles,
				numCentroids, AbstractFeatureExtractor.SURFLength));
		if (targetLength < initialLength) {
			PCA pca = new PCA(targetLength, 1, initialLength, true);
			pca.loadPCAFromFile(pcaFilename);
			ImageVectorization.setPcaProjector(pca);
		}
		imvec.setDebug(true);

		ImageVectorizationResult imvr = imvec.call();
		System.out.println(imvr.getImageName() + ": " + Arrays.toString(imvr.getImageVector()));
	}
}
