package gr.iti.mklab.visual.examples;

import gr.iti.mklab.download.ImageDownloadResult;
import gr.iti.mklab.download.ImageDownloader;
import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;

/**
 * This class demonstrates multi-threaded image download, VLAD+SURF vectorization and {@link Linear} indexing.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class UrlIndexingMT {

	public static final int maxIndexSize = 10000000;

	/**
	 * 
	 * @param args
	 *            [0] folder where temporary image files and/or thumbnails are stored
	 * @param args
	 *            [1] file containing the urls of the images that should indexed
	 * @param args
	 *            [2] directory where the BDB index will be created
	 * @param args
	 *            [3] whether to save the original images (true or false)
	 * @param args
	 *            [4] number of processor threads to be used for vectorization (compute-intensive task)
	 * @param args
	 *            [5] number of processor threads to be used for download
	 * @param args
	 *            [6] a comma separated list with full paths to the codebook files (also works for 1 codebook)
	 * @param args
	 *            [7] a comma separated list with the number of centroids in each codebook
	 * @param args
	 *            [8] path to the file containing the pca projection matrix
	 * @param args
	 *            [9] projection length
	 * @param args
	 *            [11] minimum interval between two calls in msec (e.g. 60 ~ 1000calls/min, acts as a
	 *            safeguard)
	 * @param args
	 *            [12] start indexing at this line index inclusive
	 * @param args
	 *            [13] end indexing at this line index exclusive, indexing while always stop when EOF is read
	 * @param args
	 *            [14] whether the downloader should follow redirects (true or false)
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String imageDownloadFolder = args[0];
		String urlsFile = args[1];
		String indexFolder = args[2];
		boolean saveOriginal = Boolean.parseBoolean(args[3]);
		// suggestion for compute-intensive tasks by
		// http://codeidol.com/java/java-concurrency/Applying-Thread-Pools/Sizing-Thread-Pools/
		// int numVectorizationThreads = Runtime.getRuntime().availableProcessors() + 1;
		int numVectorizationThreads = Integer.parseInt(args[4]);
		// if (numVectorizationThreads > 10) {
		// throw new Exception("Too many vectorization threads!");
		// }
		int numDownloadThreads = Integer.parseInt(args[5]);
		// if (numDownloadThreads > 50) {
		// throw new Exception("Too many download threads!");
		// }
		String[] codebookFiles = args[6].split(",");
		String[] numCentroidsString = args[7].split(",");
		int[] numCentroids = new int[numCentroidsString.length];
		for (int i = 0; i < numCentroidsString.length; i++) {
			numCentroids[i] = Integer.parseInt(numCentroidsString[i]);
		}
		String pcaFile = args[8];
		int projectionLength = Integer.parseInt(args[9]);
		int minCallInterval = Integer.parseInt(args[10]);
		int startLine = Integer.parseInt(args[11]);
		int endLine = Integer.parseInt(args[12]);
		int totalTasks = endLine - startLine;
		boolean followRedirects = Boolean.parseBoolean(args[13]);

		// Initialize the downloader, the vectorizer and the indexer
		ImageDownloader downloader = new ImageDownloader(imageDownloadFolder, numDownloadThreads);
		downloader.setSaveOriginal(saveOriginal);
		downloader.setSaveThumb(false);
		downloader.setFollowRedirects(followRedirects);
		ImageVectorizer vectorizer = new ImageVectorizer("surf", codebookFiles, numCentroids,
				projectionLength, pcaFile, numVectorizationThreads);
		// The folder where the plain index is stored.
		// String BDBEnvHome = indexFolder + "BDB_" + projectionLength + "_plain_" +
		// System.currentTimeMillis();
		String BDBEnvHome = indexFolder + "BDB_" + projectionLength;

		AbstractSearchStructure index = new Linear(projectionLength, maxIndexSize, false, BDBEnvHome, false,
				true, 0);

		BufferedReader in = new BufferedReader(new FileReader(new File(urlsFile)));
		// skip startLine lines
		for (int i = 0; i < startLine; i++) {
			in.readLine();
		}

		// scheduling!!!
		System.out.println("Indexing started!");
		long start = System.currentTimeMillis();
		int submittedDownloadsCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;

		// minimum interval between 2 download calls in msec
		long lastDownLoadCall = 0;
		String urlLine = "";
		while (true) {
			// if there are still urls to be submitted for download and the downloader's queue is not full and
			// the required interval between 2 calls has passed
			if (submittedDownloadsCounter < totalTasks && downloader.canAcceptMoreTasks()
					&& (System.currentTimeMillis() - lastDownLoadCall) >= minCallInterval
					&& (urlLine = in.readLine()) != null) {
				// parse a new line from the file
				String id;
				String url;
				// check if there is an id
				if (urlLine.split("\\s+").length > 1) {
					// assuming the id is first
					id = urlLine.split("\\s+")[0];
					url = urlLine.split("\\s+")[1];
				} else {
					id = String.valueOf(submittedDownloadsCounter + startLine);
					url = urlLine;
				}
				if (index.isIndexed(id)) {
					System.out.println("image:" + id + " already indexed");
					completedCounter++;
				} // this image has been already indexed
				else {
					downloader.submitImageDownloadTask(url, id);
					lastDownLoadCall = System.currentTimeMillis();
				}
				submittedDownloadsCounter++;
				System.out.println("Submitted download tasks: " + submittedDownloadsCounter + " ulr:" + url);
			}

			// if there is still space in the vectorizer's queue try to get an image download result and
			// to submit a new image vectorization task
			if (vectorizer.canAcceptMoreTasks()) {
				ImageDownloadResult imdr = null;
				try {
					imdr = downloader.getImageDownloadResult();
				} catch (Exception e) {
					failedCounter++;
					// e.printStackTrace();
					System.out.println(e.toString());
					System.out.println("" + new Date() + ": " + failedCounter + " vectors failed");
				}
				if (imdr != null) {
					BufferedImage image = imdr.getImage();
					// String url = download.getUrlStr();
					String id = imdr.getImageId();
					vectorizer.submitImageVectorizationTask(id, image);
				} // if a download result was successfully retrieved

			}

			// try to get an image vectorization result and to index the vector
			ImageVectorizationResult imvr = null;
			try {
				imvr = vectorizer.getImageVectorizationResult();
			} catch (Exception e) {
				failedCounter++;
				e.printStackTrace();
				System.out.println(e.toString());
				System.out.println("" + new Date() + ": " + failedCounter + " vectors failed");
			}
			if (imvr != null) {
				String name = imvr.getImageName();
				double[] vector = imvr.getImageVector();
				if (index.indexVector(name, vector)) {
					completedCounter++;
				} else {
					failedCounter++;
				}
				System.out.println("" + new Date() + ": " + completedCounter + " vectors indexed");
			}

			// check loop termination condition
			if ((completedCounter + failedCounter == totalTasks)
					|| (completedCounter + failedCounter == submittedDownloadsCounter && urlLine == null)) {
				System.out.println("Shutdown sequence has started!");
				downloader.shutDown();
				vectorizer.shutDown();
				index.close();
				in.close();
				break;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Indexing completed in: " + (end - start) + " ms");
		System.out.println(completedCounter + " indexing tasks completed!");
		System.out.println(failedCounter + " indexing tasks failed!");

	}
}
