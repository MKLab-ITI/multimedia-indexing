package gr.iti.mklab.visual.utilities;

import java.util.Arrays;

/**
 * This class contains utility methods used for vector normalization.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Normalization {

	/**
	 * This method applies L2 normalization on a given array of doubles.
	 * 
	 * @param vector
	 *            the original vector
	 * @return the L2 normalized vector
	 */
	public static double[] normalizeL2(double[] vector) {
		// compute vector 2-norm
		double norm2 = 0;
		for (int i = 0; i < vector.length; i++) {
			norm2 += vector[i] * vector[i];
		}
		norm2 = (double) Math.sqrt(norm2);

		if (norm2 == 0) {
			Arrays.fill(vector, 1);
		} else {
			for (int i = 0; i < vector.length; i++) {
				vector[i] = vector[i] / norm2;
			}
		}
		return vector;
	}

	/**
	 * This method applies L1 normalization on a given array of doubles.
	 * 
	 * @param vector
	 *            the original vector
	 * @return the L1 normalized vector
	 */
	public static double[] normalizeL1(double[] vector) {
		// compute vector 1-norm
		double norm1 = 0;
		for (int i = 0; i < vector.length; i++) {
			norm1 += Math.abs(vector[i]);
		}

		if (norm1 == 0) {
			Arrays.fill(vector, 1.0 / vector.length);
		} else {
			for (int i = 0; i < vector.length; i++) {
				vector[i] = vector[i] / norm1;
			}
		}
		return vector;
	}

	/**
	 * This method applies power normalization on a given array of doubles.
	 * 
	 * @param vector
	 *            the original vector
	 * @param aParameter
	 *            the a parameter used (usually 0.5)
	 * @return the power normalized vector
	 */
	public static double[] normalizePower(double[] vector, double aParameter) {
		for (int i = 0; i < vector.length; i++) {
			vector[i] = Math.signum(vector[i]) * Math.pow(Math.abs(vector[i]), aParameter);
		}
		return vector;
	}

	public static void main(String args[]) {
		double[] vector = new double[3];
		Arrays.fill(vector, 2);
		System.out.println(Arrays.toString(vector));
		normalizePower(vector, 0.5);
		System.out.println(Arrays.toString(vector));

	}
}
