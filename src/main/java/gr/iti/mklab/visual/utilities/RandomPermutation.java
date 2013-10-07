package gr.iti.mklab.visual.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * This class can be used for performing random permutations of vectors.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class RandomPermutation {

	/**
	 * This array contains a random permutation of the indices.
	 */
	private int[] randomlyPermutatedIndices;

	/**
	 * Constructor that initializes a random permutation of the indices.
	 * 
	 * @param seed
	 *            The seed used for generating the permutation
	 * @param dim
	 *            The dimensionality of the vectors that we want to randomly permute
	 */
	public RandomPermutation(int seed, int dim) {
		Random rand = new Random(seed);
		List<Integer> list = new ArrayList<Integer>(dim);
		for (int i = 0; i < dim; i++) {
			list.add(i);
		}
		java.util.Collections.shuffle(list, rand);
		randomlyPermutatedIndices = new int[dim];
		for (int i = 0; i < dim; i++) {
			randomlyPermutatedIndices[i] = list.get(i);
		}
	}

	/**
	 * Randomly permutes a vector using the random permutation of the indices that was created in the
	 * constructor.
	 * 
	 * @param vector
	 *            The initial vector
	 * @return The randomly permuted vector
	 */
	public double[] permute(double[] vector) {
		double[] permuted = new double[vector.length];
		for (int i = 0; i < vector.length; i++) {
			permuted[i] = vector[randomlyPermutatedIndices[i]];
		}
		return permuted;
	}

	public static void main(String args[]) {
		RandomPermutation rp = new RandomPermutation(1, 3);

		double[] vector1 = { 1, 2, 3 };
		double[] vector2 = { 4, 5, 6 };

		System.out.println(Arrays.toString(rp.permute(vector1)));
		System.out.println(Arrays.toString(rp.permute(vector1)));
		System.out.println(Arrays.toString(rp.permute(vector2)));
		System.out.println(Arrays.toString(rp.permute(vector2)));

	}
}
