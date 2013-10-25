package gr.iti.mklab.visual.extraction;

import gr.iti.mklab.visual.utilities.FeatureIO;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;

import javax.imageio.ImageIO;

/**
 * Extracts SURF or SIFT features from images contained in a directory (only .jpg or .png files) and writes a .surf(b)
 * or .sift(b) file for each image. The feature files are written in a directory named surf or sift that is created (if
 * it does not already exist) inside the images' directory.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class SURForSIFTExtractionExample {

	/**
	 * 
	 * @param args
	 *            [0] Full path to the images folder.
	 * @param args
	 *            [1] Maximum number of images to perform feature extraction on (1491 for Holidays).
	 * @param args
	 *            [2] Number of images to be skipped from extraction (usually 0).
	 * @param args
	 *            [3] Maximum number of pixels for each image (e.g. 196608 for 512x384).
	 * @param args
	 *            [4] Type of features to extract (surf or sift).
	 * @param args
	 *            [5] Whether the features should be written in binary format (true) or not (false).
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {

		String imageFolder = args[0];
		int totalImages = Integer.parseInt(args[1]);
		int skipImages = Integer.parseInt(args[2]);
		int maxPixels = Integer.parseInt(args[3]);
		String featureType = args[4];
		boolean binary = Boolean.parseBoolean(args[5]);

		ImageScaling scale = new ImageScaling(maxPixels);
		FeatureExtractor featureExtractor;
		if (featureType.equals("surf")) {
			featureExtractor = new SURFExtractor();
		} else if (featureType.equals("sift")) {
			featureExtractor = new SIFTExtractor();
		} else {
			throw new Exception("Wrong feature type provided.");
		}
		// no normalizations are performed at this point
		featureExtractor.setPowerNormalization(false);
		featureExtractor.setL2Normalization(false);

		// --------------Load the image files-------------
		File dir = new File(imageFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith(".jpg") || name.endsWith(".png"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		// create a folder for writing the features
		File file = new File(imageFolder + "/" + featureType);
		if (!file.exists()) {
			if (file.mkdir()) {
				System.out.println("Directory is created!");
			} else {
				System.out.println("Failed to create directory!");
			}
		}

		double totalReadingTime = 0;
		double totalScalingTime = 0;
		int extractedCount = 0;
		int limit = Math.min(files.length, totalImages);
		// --------------Extract features for each image-------------
		for (int i = skipImages; i < limit; i++) {
			long start = System.currentTimeMillis();
			System.out.print("Processing image " + (i + 1) + ": ");
			BufferedImage image;
			long startReading = System.currentTimeMillis();
			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFolder + files[i]));
			} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
				// This exception is probably because of a grayscale jpeg image.
				System.out.println("Exception: " + e.getMessage() + " | Image: " + files[i]);
				image = ImageIOGreyScale.read(new File(imageFolder + files[i]));
			} catch (Exception e) { // skip extraction an other exception is thrown
				System.out.println("Exception: " + e.getMessage() + " | Image: " + files[i]);
				continue;
			}
			if (image == null) {
				System.out.println("Null image: " + files[i]);
				continue;
			}

			totalReadingTime += System.currentTimeMillis() - startReading;

			long startScaling = System.currentTimeMillis();
			image = scale.maxPixelsScaling(image);
			totalScalingTime += System.currentTimeMillis() - startScaling;

			double[][] features = featureExtractor.extractFeatures(image);

			String imageFileExtension;
			if (files[i].endsWith("jpg")) {
				imageFileExtension = "jpg";
			} else {
				imageFileExtension = "png";
			}
			// write features to file
			if (binary) {
				String featuresFileName = imageFolder + "/" + featureType + "/"
						+ files[i].split("\\." + imageFileExtension)[0] + "." + featureType + "b";
				FeatureIO.writeBinary(featuresFileName, features);
			} else {
				String featuresFileName = imageFolder + "/" + featureType + "/"
						+ files[i].split("\\." + imageFileExtension)[0] + "." + featureType;
				FeatureIO.writeText(featuresFileName, features);
			}
			System.out.println("completed in " + (System.currentTimeMillis() - start) + " ms");
			extractedCount++;
		}
		System.out.println("Average reading time in ms: " + totalReadingTime / (double) extractedCount);
		System.out.println("Average scaling time in ms: " + totalScalingTime / (double) extractedCount);
		System.out.println("Average extraction time in ms: " + featureExtractor.getTotalExtractionTime()
				/ (double) extractedCount);
		System.out.println("Average number of interest points per image: "
				+ featureExtractor.getTotalNumberInterestPoints() / (double) extractedCount);

	}
}
