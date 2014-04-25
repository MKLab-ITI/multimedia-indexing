package gr.iti.mklab.visual.aggregation;

import gr.iti.mklab.visual.utilities.Normalization;

import java.util.ArrayList;

/**
 * This class computes multiple vocabulary VLAD vectors as described in: <br>
 * 
 * <em>Jégou, H., & Chum, O. (2012). Negative evidences and co-occurences in image retrieval: The benefit of PCA and whitening. In ECCV 2012.</em>
 * <br>
 * <br>
 * 
 * multiVLAD vectors are generated independently from each vocabulary and then concatenated in a single
 * vector. Standard VLAD vectors can also be generated using this class if only 1 vocabulary is provided.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class VladAggregatorMultipleVocabularies {

	/**
	 * Whether to apply the default normalization, i.e. power+L2 normalization on the subvectors and L2
	 * normalization on the concatenated vector.
	 */
	private boolean normalizationsOn = true;

	/**
	 * Length of the final vector.
	 */
	private int vectorLength;

	/**
	 * One standard VladAggregator is initialized for each vocabulary.
	 */
	private VladAggregator[] vladAggregators;

	/**
	 * Constructor. Takes as input a 3-dimensional array that contains the multiple codebooks.
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
	 * Takes as input an ArrayList of double arrays which contains the set of local descriptors of an image.
	 * Returns the multiVLAD representation of the image using the codebooks supplied in the constructor.
	 * 
	 * @param descriptors
	 * @return the multiVLAD vector
	 * @throws Exception
	 */
	public double[] aggregate(ArrayList<double[]> descriptors) throws Exception {
		double[] multiVlad = new double[vectorLength];
		int vectorShift = 0;
		for (int i = 0; i < vladAggregators.length; i++) {
			double[] subVlad = vladAggregators[i].aggregate(descriptors);
			if (normalizationsOn) {
				Normalization.normalizePower(subVlad, 0.5);
				Normalization.normalizeL2(subVlad);
			}
			System.arraycopy(subVlad, 0, multiVlad, vectorShift, subVlad.length);
			vectorShift += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}
		// re-apply l2 normalization on the concatenated vector, if we have more than 1 vocabularies
		if (vladAggregators.length > 1 && normalizationsOn) {
			Normalization.normalizeL2(multiVlad);
		}
		return multiVlad;
	}

	/**
	 * Same as {@link #aggregate(ArrayList)} but takes a two-dimensional array as input.
	 * 
	 * @param descriptors
	 * @return the multiVLAD vector
	 * @throws Exception
	 */
	public double[] aggregate(double[][] descriptors) throws Exception {
		double[] multiVlad = new double[vectorLength];
		int vectorShift = 0;
		for (int i = 0; i < vladAggregators.length; i++) {
			double[] subVlad = vladAggregators[i].aggregate(descriptors);
			if (normalizationsOn) {
				Normalization.normalizePower(subVlad, 0.5);
				Normalization.normalizeL2(subVlad);
			}
			System.arraycopy(subVlad, 0, multiVlad, vectorShift, subVlad.length);
			vectorShift += vladAggregators[i].getNumCentroids() * vladAggregators[i].getDescriptorLength();
		}
		// re-apply l2 normalization on the concatenated vector, if we have more than 1 vocabularies
		if (vladAggregators.length > 1 && normalizationsOn) {
			Normalization.normalizeL2(multiVlad);
		}
		return multiVlad;
	}

	public int getVectorLength() {
		return vectorLength;
	}

	public boolean isNormalizationsOn() {
		return normalizationsOn;
	}

	public void setNormalizationsOn(boolean normalizationsOn) {
		this.normalizationsOn = normalizationsOn;
	}

}
