package gr.iti.mklab.visual.dimreduction;

import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.utilities.Normalization;

import java.util.Arrays;

/**
 * This class can be used to perform PCA projection of a set of vectors using an already learned PCA
 * projection matrix.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class PCAProjectionExample {

	/**
	 * @param args
	 *            [0] Full path to the location of the BDB store which contains the full dimensional vectors
	 *            (use backslashes, no backslash at the end!)
	 * @param args
	 *            [1] length of the full dimensional vectors
	 * @param args
	 *            [2] number of vectors
	 * @param args
	 *            [3] full path to the file containing the PCA projection matrix
	 * @param args
	 *            [4] whether to apply whitening
	 * @param args
	 *            [5] whether to apply L2 normalization after PCA (note: when whitening is true the vectors
	 *            are already L2 normalized)
	 * @param args
	 *            [6] a comma separated list of the desired projection lengths in increasing order
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {

		String fullVectorsIndexLocation = args[0];
		int initialVectorLength = Integer.parseInt(args[1]);
		int maxNumVectors = Integer.parseInt(args[2]);
		String PCAFileName = args[3];
		boolean whitening = Boolean.parseBoolean(args[4]);
		boolean L2Normalization = Boolean.parseBoolean(args[5]);
		String[] projectionLengthsString = args[6].split(",");
		int[] projectionLengths = new int[projectionLengthsString.length];
		for (int i = 0; i < projectionLengthsString.length; i++) {
			projectionLengths[i] = Integer.parseInt(projectionLengthsString[i]);
		}

		Linear fullVectors = new Linear(initialVectorLength, maxNumVectors, true, fullVectorsIndexLocation,
				false, true, 0);
		int actualNumVectors = fullVectors.getLoadCounter();
		int numVectors = Math.min(actualNumVectors, maxNumVectors);

		// Loading the pca matrix
		int numComponents = projectionLengths[projectionLengths.length - 1]; // the largest projection length
		PCA pca = new PCA(numComponents, 1, initialVectorLength, whitening);
		pca.loadPCAFromFile(PCAFileName);

		// Initializing the indices
		Linear[] projectedVectors = new Linear[projectionLengths.length];
		for (int i = 0; i < projectedVectors.length; i++) {
			String projectedVectorsIndexLocation = fullVectorsIndexLocation + "to" + projectionLengths[i];
			if (whitening) {
				projectedVectorsIndexLocation += "w";
			}
			if (L2Normalization) {
				projectedVectorsIndexLocation += "_l2";
			}
			projectedVectors[i] = new Linear(projectionLengths[i], numVectors, false,
					projectedVectorsIndexLocation, false, true, 0);
		}

		for (int i = 0; i < numVectors; i++) {
			String id = fullVectors.getId(i);
			double[] fullVec = fullVectors.getVector(i);
			// projection is done only once, to the largest dimension
			double[] projectedVec = pca.sampleToEigenSpace(fullVec);

			for (int j = 0; j < projectedVectors.length; j++) {
				// the projected vector is then truncated to the appropriate length.
				double[] truncatedprojectedVec = Arrays.copyOf(projectedVec, projectionLengths[j]);
				if (L2Normalization) {
					Normalization.normalizeL2(truncatedprojectedVec);
				}
				projectedVectors[j].indexVector(id, truncatedprojectedVec);
			}
		}

	}
}
