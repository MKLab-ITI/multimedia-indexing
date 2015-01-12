package gr.iti.mklab.visual.examples;

import gr.iti.mklab.download.ImageDownload;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.datastructures.PQ.TransformationType;
import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.Result;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * This class gives an example of how a precomputed IVFPQ index of the <a
 * href="http://labs.yahoo.com/news/yfcc100m/">YFCC100M</a> collection can be loaded and used to answer
 * queries using the <a href="https://github.com/socialsensor/multimedia-indexing">multimedia-indexing</a>
 * library that was developed within SocialSensor EU Project.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 *
 */
public class YFCC100MExample {

	/**
	 * The following operations are performed:
	 * <ul>
	 * <li>The IVFPQ index is loaded in memory</li>
	 * <li>The coarse and product quantizer are loaded from files.</li>
	 * <li>An image vectorizer is initialized (i.e. codebooks and pca matrix are loaded).</li>
	 * <li>A query image is downloaded from the supplied url.</li>
	 * <li>The image is vectorized and the vector is used to query the index.</li>
	 * <li>Most similar image urls are printed.</li>
	 * </ul>
	 * 
	 * Depending on the number of images that are loaded, a sufficient amount of memory should be allocated
	 * using the -Xmx command (use -Xmx12g to load the full collection).
	 * 
	 * @param args
	 *            [0] Path to the folder where the IVFPQ index resides
	 * @param args
	 *            [1] Number of images (i.e. vectors) to load. This number should be equal or smaller to the
	 *            total size of the index (~100M).
	 * @param args
	 *            [2] Path to the folder where the learning files reside
	 * @param args
	 *            [3] URL of a query image
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String ivfPqIndexPath = args[0];
		int maxNumVectors = Integer.parseInt(args[1]);
		String learningFilesPath = args[2];
		String url = args[3];

		String coarseQuantizerFilename = learningFilesPath + "qcoarse_1024d_8192k.csv";
		String productQuantizerFilename = learningFilesPath + "pq_1024_64x8_rp_ivf_8192k.csv";
		String[] codebookFiles = new String[4];
		for (int i = 0; i < 4; i++) {
			codebookFiles[i] = learningFilesPath + "surf_l2_128c_" + i + ".csv";
		}
		String pcaFile = learningFilesPath + "pca_surf_4x128_32768to1024.txt";

		/** parameters of the index */
		boolean readonly = true;
		boolean countSizeOnLoad = false;
		int loadCounter = maxNumVectors;
		boolean loadIndexInMemory = true;
		int projectionLength = 1024;
		/** parameters of the product quantizer */
		int numCoarseCentroids = 8192;
		int numSubVectors = 64;
		int numProductCentroids = 256;
		TransformationType transformation = PQ.TransformationType.RandomPermutation;

		// Create an IVFPQ object and load the index in memory
		IVFPQ ivfpq = new IVFPQ(projectionLength, maxNumVectors, readonly, ivfPqIndexPath, numSubVectors,
				numProductCentroids, transformation, numCoarseCentroids, countSizeOnLoad, loadCounter,
				loadIndexInMemory);
		System.out.print("Loading coarse and product quantizer..");
		ivfpq.loadCoarseQuantizer(coarseQuantizerFilename);
		ivfpq.loadProductQuantizer(productQuantizerFilename);
		ivfpq.setW(w);
		System.out.println("..completed!");

		System.out.println("Initializing the vectorizer..");
		int[] numCentroids = { 128, 128, 128, 128 };
		ImageVectorizer vectorizer = new ImageVectorizer("surf", codebookFiles, numCentroids,
				projectionLength, pcaFile, true, 1);
		// set a max size similar to that of the indexed images!
		vectorizer.setMaxImageSizeInPixels(maxSizeInPixels);
		System.out.println("..completed!");

		String id = "1"; // a dummy id is given
		String downloadFolder = "";
		ImageDownload imd = new ImageDownload(url, id, downloadFolder, false, true, false);
		System.out.print("Downloading the image..");
		BufferedImage image = imd.downloadImage();
		System.out.println("..completed!");

		System.out.print("Computing the vector..");
		vectorizer.submitImageVectorizationTask(id, image);
		ImageVectorizationResult result = vectorizer.getImageVectorizationResultWait();
		double[] vector = result.getImageVector();
		vectorizer.shutDown();
		System.out.println("Query vector: " + Arrays.toString(Arrays.copyOf(vector, 10)));
		System.out.println("..completed!");

		System.out.print("Computing neighbors..");
		Answer ans = ivfpq.computeNearestNeighbors(numNeighbors, vector);
		System.out.println("..completed!");

		double searchTime = (double) ans.getIndexSearchTime() / 1000000.0;
		double lookupTime = (double) ans.getNameLookupTime() / 1000000.0;
		System.out.println("Search time: " + searchTime + " ms");
		System.out.println("Lookup time: " + lookupTime + " ms");

		Result[] results = ans.getResults();
		for (int i = 0; i < results.length; i++) {
			String resultUrl = decodeUrl(results[i].getExternalId());
			System.out.println(resultUrl + " " + results[i].getDistance());
			try {// downloading the image
				String qid = "nn_" + i;
				imd = new ImageDownload(resultUrl, qid, downloadFolder, false, true, false);
				System.out.print("Downloading result image..");
				imd.downloadImage();
				System.out.println("..completed!");
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

	}

	/**
	 * Number of nearest neighbors to return.
	 */
	public static final int numNeighbors = 10;
	/**
	 * The query image is downscaled to this maximum size (in pixels) if larger.
	 */
	public static final int maxSizeInPixels = 640 * 480;

	/**
	 * Number of list to be searched out of 8192.
	 */
	public static final int w = 10;

	/**
	 * This method is used to compile the Flickr URL from its parts.
	 * 
	 * @param endcodedUrl
	 *            The URL in an encoded form.
	 * @return The decoded URL.
	 */
	private static String decodeUrl(String endcodedUrl) {
		String imageSize = "z"; // [mstzb]
		String[] parts = endcodedUrl.split("_");
		String farmId = parts[0];
		String serverId = parts[1];
		String identidier = parts[2];
		String secret = parts[3];
		// String isVideo = parts[4];

		String url = "https://farm" + farmId + ".staticflickr.com/" + serverId + "/" + identidier + "_"
				+ secret + "_" + imageSize + ".jpg";
		return url;
	}
}
