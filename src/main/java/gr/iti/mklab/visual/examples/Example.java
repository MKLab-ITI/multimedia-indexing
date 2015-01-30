package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.Normalization;
import gr.iti.mklab.visual.utilities.Result;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Example {

	public static void main(String[] args) throws Exception {

		// parameters
		int maxNumPixels = 768 * 512; // use 1024*768 for better/slower extraction
		int[] numCentroids = { 128, 128, 128, 128 };

		int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;

		int targetLengthMax = 1024;

		// int targetLength1 = 256;
		int targetLength2 = 1024;
		// int targetLength3 = 1024;

		int maximumNumVectors = 1000000;

		// String indexFolder = "/home/manos/git/multimedia-indexing/indices/";

		// String fullVectorIndexFolder = indexFolder + "main";

		// int m1 = 16; // num subvectors
		// String ivfpqIndex1Folder = indexFolder + "cheap";

		int m2 = 64;
		// String ivfpqIndex2Folder = indexFolder + "medium";

		// int m3 = 128;
		// String ivfpqIndex3Folder = "best";

		String linearIndexFolder = "/disk2_data/VisualIndex/data/prototype/linear";
		String ivfpqIndexFolder = "/disk2_data/VisualIndex/data/prototype/ivfpq";

		int k_c = 256;
		int numCoarseCentroids = 8192;

		String learningFolder = "/home/manosetro/git/multimedia-indexing/learning_files/";

		/*
		 * String[] codebookFiles = { learningFolder + "surf_l2_128c_0.csv", learningFolder +
		 * "surf_l2_128c_1.csv", learningFolder + "surf_l2_128c_2.csv", learningFolder + "surf_l2_128c_3.csv"
		 * };
		 * 
		 * String pcaFile = learningFolder + "pca_surf_4x128_32768to1024.txt";
		 */

		// String coarseQuantizerFile1 = learningFolder + "qcoarse_256d_8192k.csv";
		String coarseQuantizerFile2 = learningFolder + "qcoarse_1024d_8192k.csv";
		// String coarseQuantizerFile3 = learningFolder + "qcoarse_1024d_8192k.csv";

		// String productQuantizerFile1 = learningFolder + "pq_256_16x8_rp_ivf_k8192.csv";
		String productQuantizerFile2 = learningFolder + "pq_1024_64x8_rp_ivf_8192k.csv";
		// String productQuantizerFile3 = learningFolder + "pq_1024_128x8_rp_ivf_8192k.csv";

		/*
		 * ImageVectorization.setFeatureExtractor(new SURFExtractor());
		 * ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles,
		 * numCentroids, AbstractFeatureExtractor.SURFLength));
		 * 
		 * if (targetLengthMax < initialLength) { PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
		 * pca.loadPCAFromFile(pcaFile); ImageVectorization.setPcaProjector(pca); }
		 */

		// System.out.println("PCA loaded!");

		// creating/loading the indices
		// this linear index contains plain 1024d vectors and is not loaded in memory
		Linear linear = new Linear(targetLengthMax, targetLengthMax, false, linearIndexFolder, true, true, 0);

		// this is the cheaper IVFPQ index and is loaded in memory
		IVFPQ ivfpq_1 = new IVFPQ(targetLength2, maximumNumVectors, false, ivfpqIndexFolder, m2, k_c,
				PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0, true, 512);
		ivfpq_1.loadCoarseQuantizer(coarseQuantizerFile2);
		ivfpq_1.loadProductQuantizer(productQuantizerFile2);
		int w = 64; // larger values will improve results/increase seach time
		ivfpq_1.setW(w); // how many (out of 8192) lists should be visited during search.

		System.out.println("Indices created!");

		for (int i = 0; i < linear.getLoadCounter(); i++) {

			String id = linear.getId(i);

			System.out.println(i + " => " + id);

			double[] vector = linear.getVector(i);
			System.out.println(vector.length);

			Answer r = ivfpq_1.computeNearestNeighbors(100, vector);
			System.out.println(r.getResults().length);
		}
		// IVFPQ ivfpq_2 = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndex1Folder, m2, k_c,
		// PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		// ivfpq_2.loadCoarseQuantizer(coarseQuantizerFile2);
		// ivfpq_2.loadProductQuantizer(productQuantizerFile2);
		// IVFPQ ivfpq_3 = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndex1Folder, m3, k_c,
		// PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		// ivfpq_3.loadCoarseQuantizer(coarseQuantizerFile3);
		// ivfpq_3.loadProductQuantizer(productQuantizerFile3);

		// File imageFolder = new File("/media/manos/Data/Pictures/Sofia 2013");

		// // setting up the vectorizer and extracting the vector of a single image
		// for(String imageFilename : imageFolder.list()) {
		// if(!imageFilename.endsWith("JPG"))
		// continue;
		//
		// System.out.println(imageFilename);
		// ImageVectorization imvec = new ImageVectorization(imageFolder.toString()+"/", imageFilename,
		// targetLengthMax,
		// maxNumPixels);
		//
		// ImageVectorizationResult imvr = imvec.call();
		// double[] vector = imvr.getImageVector();
		// System.out.println(Arrays.toString(vector));
		//
		//
		// // indexing a vector
		// String id = imageFilename;
		//
		// // the full vector is indexed in the disk-based index
		// //linear.indexVector(id, vector);
		//
		// // the vector is truncated to the correct dimension and renormalized before sending to the
		// ram-based index
		// double[] newVector = Arrays.copyOf(vector, targetLength1);
		// if (newVector.length < vector.length) {
		// Normalization.normalizeL2(newVector);
		// }
		// ivfpq_1.indexVector(id, newVector);
		//
		// }

		/*
		 * int p=0, n=0; for(String imageFilename : imageFolder.list()) { if(!imageFilename.endsWith("JPG"))
		 * continue;
		 * 
		 * // indexing a vector String id = imageFilename;
		 * 
		 * // querying the ram-based index with a vector
		 * 
		 * // this a vector from an already indexed image int iid = linear.getInternalId(id); double[] qVector
		 * = linear.getVector(iid);
		 * 
		 * int k = 5; // nearest neighbors System.out.println("Image : " + id);
		 * 
		 * long t = System.currentTimeMillis(); Result[] exactResults = linear.computeNearestNeighbors(k,
		 * qVector).getResults(); t = System.currentTimeMillis() - t; System.out.println("Exact results (" +
		 * exactResults.length + ") in " + t + " msecs!"); Set<String> set1 = new HashSet<String>(); for
		 * (Result r : exactResults) { set1.add(r.getId()); }
		 * 
		 * t = System.currentTimeMillis(); Result[] approximateResults = ivfpq_1.computeNearestNeighbors(k,
		 * qVector).getResults(); t = System.currentTimeMillis() - t;
		 * System.out.println("Approximate results (" + approximateResults.length + ") in " + t + " msecs!");
		 * Set<String> set2 = new HashSet<String>(); for (Result r : approximateResults) {
		 * set2.add(r.getId()); }
		 * 
		 * set2.retainAll(set1); p += set2.size(); n += 5; System.out.println("Jaccard: " + set2.size() / 5.);
		 * System.out.println("==================================================="); }
		 * System.out.println("Jaccard: " + (double)p / (double)n);
		 */

	}
}