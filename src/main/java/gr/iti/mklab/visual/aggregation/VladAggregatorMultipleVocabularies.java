package gr.iti.mklab.visual.aggregation;

import gr.iti.mklab.visual.utilities.Normalization;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class computes multiple vocabulary VLAD vectors as described in: <br>
 * 
 * <pre>
 * H. Jegou and O. Chum, Negative evidences and co-occurences in image retrieval: The benefit of pca and whitening, in ECCV, 2012
 * </pre>
 * 
 * VLAD vectors are generated independently from each vocabulary using power+L2 normalization and then
 * concatenated in a single vector that is L2 normalized.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class VladAggregatorMultipleVocabularies {

	private int vectorLength;

	private VladAggregator[] vladAggregators;

	/**
	 * Constructor. Takes as input 3-dimensional array which contains the multiple codebooks.
	 * 
	 * @param codebook
	 */
	public VladAggregatorMultipleVocabularies(double[][][] codebooks) {
		vladAggregators = new VladAggregator[codebooks.length];
		for (int i = 0; i < codebooks.length; i++) {
			vladAggregators[i] = new VladAggregator(codebooks[i]);
			vectorLength += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}
	}

	/**
	 * 
	 * @throws IOException
	 */
	public VladAggregatorMultipleVocabularies(String[] codebookFiles, int[] numCentroids, int featureLength)
			throws IOException {
		int numCodebooks = codebookFiles.length;
		// initialize the VLAD object
		double[][][] codebooks = new double[numCodebooks][][];
		for (int i = 0; i < numCodebooks; i++) {
			codebooks[i] = AbstractFeatureAggregator.readQuantizer(codebookFiles[i], numCentroids[i],
					featureLength);
		}
		vladAggregators = new VladAggregator[codebooks.length];
		for (int i = 0; i < codebooks.length; i++) {
			vladAggregators[i] = new VladAggregator(codebooks[i]);
			vectorLength += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}
	}

	/**
	 * Takes as input an ArrayList of double arrays which contains the set of local descriptors of an image.
	 * Returns the VLAD vector representation of the image using the codebooks supplied in the constructor.
	 * TODO: A vectorized implementation might be faster.
	 * 
	 * @param descriptors
	 * @return the VLAD vector
	 * @throws Exception
	 */
	public double[] aggregate(ArrayList<double[]> descriptors) throws Exception {
		double[] finalVlad = new double[vectorLength];
		int vectorShift = 0;
		for (int i = 0; i < vladAggregators.length; i++) {
			double[] subVlad = vladAggregators[i].aggregate(descriptors);
			Normalization.normalizePower(subVlad, 0.5);
			Normalization.normalizeL2(subVlad);
			System.arraycopy(subVlad, 0, finalVlad, vectorShift, subVlad.length);
			vectorShift += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}
		// re-apply l2 normalization on the concatenated vector, if we have more than 1 vocabularies
		if (vladAggregators.length > 1) {
			Normalization.normalizeL2(finalVlad);
		}
		return finalVlad;
	}

	/**
	 * Same as {@link #aggregate(ArrayList)} but takes a two-dimensional array as input.
	 * 
	 * @param descriptors
	 * @return the VLAD vector
	 * @throws Exception
	 */
	public double[] aggregate(double[][] descriptors) throws Exception {
		// concatenation
		double[] finalVlad = new double[vectorLength];
		int vectorShift = 0;
		for (int i = 0; i < vladAggregators.length; i++) {
			double[] subVlad = vladAggregators[i].aggregate(descriptors);
			Normalization.normalizePower(subVlad, 0.5);
			Normalization.normalizeL2(subVlad);
			System.arraycopy(subVlad, 0, finalVlad, vectorShift, subVlad.length);
			vectorShift += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}

		// re-apply l2 normalization on the concatenated vector, if we have more than 1 vocabularies
		if (vladAggregators.length > 1) {
			Normalization.normalizeL2(finalVlad);
		}
		return finalVlad;
	}

	public int getVectorLength() {
		return vectorLength;
	}

}
