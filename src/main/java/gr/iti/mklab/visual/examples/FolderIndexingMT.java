package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Date;

/**
 * This class demonstrates multi-threaded VLAD vectorization and @{link Linear} indexing of the images in a
 * given folder.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class FolderIndexingMT {

	/** A maximum size for the index */
	public static final int maxIndexSize = 10000000;

	/**
	 * @param args
	 *            [0] directory that contains the image files
	 * @param args
	 *            [1] directory where the index will be created
	 * @param args
	 *            [2] a comma separated list with full paths to the codebook files (also works for 1 codebook)
	 * @param args
	 *            [3] a comma separated list with the sizes of the codebooks
	 * @param args
	 *            [4] path to the file containing the pca projection matrix (an empty String can be given if
	 *            no projection is needed)
	 * @param args
	 *            [5] projection length (if projection length >= initial length, no projection will be
	 *            performed)
	 * @param args
	 *            [6] images will be scaled at this maximum number of pixels before vectorization
	 * @param args
	 *            [7] number of processor threads to be used for vectorization (compute-intensive task)
	 * @param args
	 *            [8] type of features to be extracted (surf/sift/rootsift/csurf)
	 * @param args
	 *            [9] whether whitening should be applied along with PCA projection (true/false)
	 */
	public static void main(String[] args) throws Exception {

		String imagesFolder = args[0];
		String indexFolder = args[1];
		String[] codebookFiles = args[2].split(",");
		String[] numCentroidsString = args[3].split(",");
		int[] numCentroids = new int[numCentroidsString.length];
		for (int i = 0; i < numCentroidsString.length; i++) {
			numCentroids[i] = Integer.parseInt(numCentroidsString[i]);
		}
		String pcaFile = args[4];
		int projectionLength = Integer.parseInt(args[5]);
		int maxImageSizeInPixels = Integer.parseInt(args[6]);
		// suggestion for compute-intensive tasks by
		// http://codeidol.com/java/java-concurrency/Applying-Thread-Pools/Sizing-Thread-Pools/
		// int numVectorizationThreads = Runtime.getRuntime().availableProcessors() + 1;
		int numVectorizationThreads = Integer.parseInt(args[7]);
		String featureType = args[8];
		boolean whitening = Boolean.parseBoolean(args[9]);

		// Initialize the vectorizer and the indexer
		ImageVectorizer vectorizer = new ImageVectorizer(featureType, codebookFiles, numCentroids,
				projectionLength, pcaFile, whitening, numVectorizationThreads);
		vectorizer.setMaxImageSizeInPixels(maxImageSizeInPixels);
		String BDBEnvHome = indexFolder + "BDB_" + maxImageSizeInPixels + "_" + featureType + "_"
				+ vectorizer.getInitialVectorLength();
		if (projectionLength < vectorizer.getInitialVectorLength()) {
			BDBEnvHome += "to" + projectionLength;
		}
		if (whitening) {
			BDBEnvHome += "w";
		}

		AbstractSearchStructure index = new Linear(projectionLength, maxIndexSize, false, BDBEnvHome, false,
				true, 0);

		// load the images
		File dir = new File(imagesFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".jpg")
						|| name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".gif"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		// scheduling!!!
		System.out.println("Indexing started!");
		long start = System.currentTimeMillis();
		int submittedVectorizationsCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;

		while (true) {
			// if we can submit more tasks to the vectorizer then do it
			if (submittedVectorizationsCounter < files.length && vectorizer.canAcceptMoreTasks()) {
				// avoid extraction if an image is already indexed
				String imageName = files[submittedVectorizationsCounter];
				if (index.isIndexed(imageName)) { // this image has been already indexed
					System.out.println("image:" + imageName + " already indexed");
					completedCounter++;
				} else {
					vectorizer.submitImageVectorizationTask(imagesFolder, imageName);
				}
				submittedVectorizationsCounter++;
				System.out.println("Submitted vectorization tasks: " + submittedVectorizationsCounter
						+ " image:" + imageName);
			}

			// try to get an image vectorization result and to index the vector
			ImageVectorizationResult imvr = null;
			// try {
			imvr = vectorizer.getImageVectorizationResult();
			// } catch (Exception e) {
			// // this code will probably be never executed because getImageVectorizationResult() does not
			// // throw Exceptions anymore!
			// failedCounter++;
			// e.printStackTrace();
			// System.out.println(e.toString());
			// System.out.println("" + new Date() + ": " + failedCounter + " vectors failed");
			// System.out.println("Image: " + imvr.getImageName());
			// }

			if (imvr != null) {
				String name = imvr.getImageName();
				name = name.split("\\.")[0] + ".jpg";
				if (imvr.getExceptionMessage() == null) {
					// vectorization completed with success!s
					double[] vector = imvr.getImageVector();
					if (index.indexVector(name, vector)) {
						completedCounter++;
					} else {
						failedCounter++;
					}
					System.out.println("" + new Date() + ": " + completedCounter + " vectors indexed");
				} else {
					// something bad happened during vectorization
					System.out.println("Something bad happened when vectorizing image: " + name);
					System.out.println("Exception message: " + imvr.getExceptionMessage());
					failedCounter++;
					System.out.println("" + new Date() + ": " + failedCounter + " vectors failed");

				}

			}

			// check loop termination condition
			if ((completedCounter + failedCounter == files.length)) {
				System.out.println("Shutdown sequence has started!");
				vectorizer.shutDown();
				index.close();
				break;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start) + " ms");
		System.out.println(completedCounter + " indexing tasks completed!");
	}
}
