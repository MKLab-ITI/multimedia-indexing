package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.Normalization;
import gr.iti.mklab.visual.utilities.Result;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;

import java.io.File;
import java.util.Arrays;

public class Example {

	public static void main(String[] args) throws Exception {
	
		// parameters
		int maxNumPixels = 768 * 512; // use 1024*768 for better/slower extraction
		int[] numCentroids = { 128, 128, 128, 128 };
		
		int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
		
		int targetLengthMax = 1024;
		
		//int targetLength1 = 256;
		int targetLength2 = 1024;
		//int targetLength3 = 1024;
		
		int maximumNumVectors = 1000000;
		
		String indexFolder = "/home/manosetro/git/multimedia-indexing/indeces/";
		
		String fullVectorIndexFolder = indexFolder + "main";
		
		
		//int m1 = 16; // num subvectors
		//String ivfpqIndex1Folder = indexFolder + "cheap";
		
		int m2 = 64;
		String ivfpqIndex2Folder = indexFolder + "medium";
		
		// int m3 = 128;
		// String ivfpqIndex3Folder = "best";
		
		
		int k_c = 256;
		int numCoarseCentroids = 8192;

		String learningFolder = "/home/manosetro/git/multimedia-indexing/learning_files/";
		
		String[] codebookFiles = { 
				learningFolder + "surf_l2_128c_0.csv",
				learningFolder + "surf_l2_128c_1.csv", 
				learningFolder + "surf_l2_128c_2.csv",
				learningFolder + "surf_l2_128c_3.csv" 
		};
		
		String pcaFile = learningFolder + "pca_surf_4x128_32768to1024.txt";
		
		
		//String coarseQuantizerFile1 = learningFolder + "qcoarse_256d_8192k.csv";
		String coarseQuantizerFile2 = learningFolder + "qcoarse_1024d_8192k.csv";
		//String coarseQuantizerFile3 = learningFolder + "qcoarse_1024d_8192k.csv";
		
		
		//String productQuantizerFile1 = learningFolder + "pq_256_16x8_rp_ivf_k8192.csv";
		String productQuantizerFile2 = learningFolder + "pq_1024_64x8_rp_ivf_8192k.csv";
		//String productQuantizerFile3 = learningFolder + "pq_1024_128x8_rp_ivf_8192k.csv";

		ImageVectorization.setFeatureExtractor(new SURFExtractor());
		ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles,
				numCentroids, AbstractFeatureExtractor.SURFLength));
		if (targetLengthMax < initialLength) {
			PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
			pca.loadPCAFromFile(pcaFile);
			ImageVectorization.setPcaProjector(pca);
		}
		System.out.println("PCA loaded!");
		
		// creating/loading the indices
		// this linear index contains plain 1024d vectors and is not loaded in memory
		Linear linear = new Linear(targetLengthMax, targetLengthMax, false, fullVectorIndexFolder, false, true, 0);
		
		// this is the cheaper IVFPQ index and is loaded in memory
		IVFPQ ivfpq_1 = new IVFPQ(targetLengthMax, maximumNumVectors, false, ivfpqIndex2Folder, m2, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		ivfpq_1.loadCoarseQuantizer(coarseQuantizerFile2);
		ivfpq_1.loadProductQuantizer(productQuantizerFile2);
		int w = 64; // larger values will improve results/increase seach time
		ivfpq_1.setW(w); // how many (out of 8192) lists should be visited during search.
			
		System.out.println("Indices created!");
		
		// IVFPQ ivfpq_2 = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndex1Folder, m2, k_c,
		// PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		// ivfpq_2.loadCoarseQuantizer(coarseQuantizerFile2);
		// ivfpq_2.loadProductQuantizer(productQuantizerFile2);
		// IVFPQ ivfpq_3 = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndex1Folder, m3, k_c,
		// PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		// ivfpq_3.loadCoarseQuantizer(coarseQuantizerFile3);
		// ivfpq_3.loadProductQuantizer(productQuantizerFile3);		
				
				
		/*
		// setting up the vectorizer and extracting the vector of a single image
		File imageFolder = new File("/disk1_data/Photos/AkisGenethlia");
		for(String imageFilename : imageFolder.list()) {
			if(!imageFilename.endsWith("jpg"))
				continue;
			
			System.out.println(imageFilename);
			ImageVectorization imvec = new ImageVectorization(imageFolder.toString()+"/", imageFilename, targetLengthMax, maxNumPixels);
			
			ImageVectorizationResult imvr = imvec.call();
			double[] vector = imvr.getImageVector();
			System.out.println(Arrays.toString(vector));
			
			
			// indexing a vector
			String id = imageFilename;
			
			// the full vector is indexed in the disk-based index
			linear.indexVector(id, vector);
			
			// the vector is truncated to the correct dimension and renormalized before sending to the ram-based index
			double[] newVector = Arrays.copyOf(vector, targetLength2);
			if (newVector.length < vector.length) {
				Normalization.normalizeL2(newVector);
			}
			ivfpq_1.indexVector(id, newVector);
			
//			// querying the ram-based index with a vector
//			
//			// this a vector from an already indexed image
//			int iid = linear.getInternalId(id);
//			double[] qVector = linear.getVector(iid);
//
//			int k = 10; // nearest neighbors
//			Result[] results = ivfpq_1.computeNearestNeighbors(k, newVector).getResults();
//			for (Result r : results) {
//				String eid = r.getId();
//				int rid = r.getInternalId();
//				double distance = r.getDistance();
//				
//				System.out.println(eid + " (" + rid + ") " + " : " + distance);
//			}
		}
		*/

	}
}