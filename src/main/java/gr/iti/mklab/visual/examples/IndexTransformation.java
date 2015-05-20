package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.utilities.Normalization;

import java.util.Arrays;

/**
 * This class can be used for transforming an existing {@link Linear} index (BDB store) of unit length vectors
 * into a different type. The following transformations are supported:
 * 
 * <ol>
 * <li>Transform into a {@link Linear} index of lower-dimensional unit length vectors by truncating and
 * re-normalizing.</li>
 * <li>Transform into a {@link PQ} index using the supplied product quantizer and parameters.</li>
 * <li>Transform into an {@link IVFPQ} index using the supplied coarse and product quantizers and parameters.</li>
 * </ol>
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class IndexTransformation {

	/**
	 * @param args
	 *            [0] Full path to the original index.
	 * @param args
	 *            [1] Full path to the target index.
	 * @param args
	 *            [2] Length of the original index vectors.
	 * @param args
	 *            [3] Length of the target index vectors.
	 * @param args
	 *            [4] Number of vectors to transform.
	 * @param args
	 *            [5] The type of transformation to be applied, one of small/pq/ivfpq.
	 *            <p>
	 *            The following parameters are used only if pq transformation is selected.
	 *            </p>
	 * @param args
	 *            [6] Full path to the product quantizer file.
	 * @param args
	 *            [7] m parameter (number of subquantizers) of the product quantizer.
	 * @param args
	 *            [8] k_s parameter (centroids of each subquantizer) of the product quantizer.
	 * @param args
	 *            [9] the type of transformation to perform on the vectors prior to product quantization, one
	 *            of no/rr/rp.
	 *            <p>
	 *            The following parameters are used only if ivfpq transformation is selected.
	 *            </p>
	 * @param args
	 *            [10] Full path to the coarse quantizer file.
	 * @param args
	 *            [11] k_c parameter (number of centroids) of the coarse quantizer.
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String initialIndexFolder = args[0];
		String targetIndexFolder = args[1];
		int initialVectorLength = Integer.parseInt(args[2]);
		int targetVectorLength = Integer.parseInt(args[3]);
		int maxNumIndexedVectors = Integer.parseInt(args[4]);
		String transfomationType = args[5].toLowerCase();

		// load existing index
		Linear fromIndex = new Linear(initialVectorLength, maxNumIndexedVectors, true, initialIndexFolder,
				false, true, 0);

		// initialize target index
		AbstractSearchStructure toIndex = null;
		if (transfomationType.equals("small")) {
			// === re-index to a smaller plain index ===
			toIndex = new Linear(targetVectorLength, maxNumIndexedVectors, false, targetIndexFolder, false,
					true, 0);
		} else if (transfomationType.equals("pq") || transfomationType.equals("ivfpq")) {
			String productQuantizerFile = args[6];
			int m = Integer.parseInt(args[7]);
			int k_s = Integer.parseInt(args[8]);
			String transformationTypeString = args[9];
			PQ.TransformationType transformation;
			if (transformationTypeString.equals("no")) {
				transformation = PQ.TransformationType.None;
			} else if (transformationTypeString.equals("rr")) {
				transformation = PQ.TransformationType.RandomRotation;
			} else if (transformationTypeString.equals("rp")) {
				transformation = PQ.TransformationType.RandomPermutation;
			} else {
				throw new Exception("Wrong transformation type given!");
			}
			if (transfomationType.equals("pq")) {// pq
				// === re-index to an PQ index ===
				toIndex = new PQ(targetVectorLength, maxNumIndexedVectors, false, targetIndexFolder, m, k_s,
						transformation, 512);
				((PQ) toIndex).loadProductQuantizer(productQuantizerFile);
			} else { // ivfpq
				String coarseQuantizerFile = args[10];
				int k_c = Integer.parseInt(args[11]);
				// === re-index to an IVFPQ index ===
				toIndex = new IVFPQ(targetVectorLength, maxNumIndexedVectors, false, targetIndexFolder, m,
						k_s, transformation, k_c, 512);
				((IVFPQ) toIndex).loadCoarseQuantizer(coarseQuantizerFile);
				((IVFPQ) toIndex).loadProductQuantizer(productQuantizerFile);
			}
		} else {
			throw new Exception("Unsupported index transformation type!");
		}

		for (int i = 0; i < maxNumIndexedVectors; i++) {
			String id = fromIndex.getId(i);
			double[] vector = fromIndex.getVector(i);
			// truncate vector to the target length and re-normalize
			double[] newVector = Arrays.copyOf(vector, targetVectorLength);
			if (newVector.length < vector.length) {
				Normalization.normalizeL2(newVector);
			}
			toIndex.indexVector(id, newVector);
		}

		toIndex.close();
	}
}
