package gr.iti.mklab.visual.dimreduction;

import gr.iti.mklab.visual.datastructures.Linear;

/**
 * This class can be used to learn a PCA projection matrix.
 * 
 * @author Elefterios Spyromitros-Xioufis
 */
public class PCALearningExample {

	/**
	 * This method can be used to learn a PCA projection matrix.
	 * 
	 * @param args
	 *            [0] full path to the location of the BDB store which contains the training vectors (use
	 *            backslashes)
	 * @param args
	 *            [1] number of vectors to use for learning (the first vectors will be used), e.g. 10000
	 * @param args
	 *            [2] length of the supplied vectors, e.g. 4096 (for VLAD+SURF vectors generated using 64
	 *            centroids)
	 * @param args
	 *            [3] number of first principal components to be kept, e.g. 1024
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String indexLocation = args[0];
		int numTrainVectors = Integer.parseInt(args[1]);
		int vectorLength = Integer.parseInt(args[2]);
		int numPrincipalComponents = Integer.parseInt(args[3]);
		boolean whitening = true;
		boolean compact = false;

		PCA pca = new PCA(numPrincipalComponents, numTrainVectors, vectorLength, whitening);
		pca.setCompact(compact);

		// load the vectors into the PCA class
		Linear vladArray = new Linear(vectorLength, numTrainVectors, true, indexLocation, false, true, 0);
		for (int i = 0; i < numTrainVectors; i++) {
			pca.addSample(vladArray.getVector(i));
		}

		// now we are able to perform SVD and compute the eigenvectors
		System.out.println("PCA computation started!");
		long start = System.currentTimeMillis();
		pca.computeBasis();
		long end = System.currentTimeMillis();
		System.out.println("PCA computation completed in " + (end - start) + " ms");

		// now we can save the PCA matrix in a file
		String PCAfile = indexLocation + "pca_" + numTrainVectors + "_" + numPrincipalComponents + "_"
				+ (end - start) + "ms.txt";
		pca.savePCAToFile(PCAfile);
	}

}