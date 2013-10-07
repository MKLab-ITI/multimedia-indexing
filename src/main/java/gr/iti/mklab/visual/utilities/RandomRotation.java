package gr.iti.mklab.visual.utilities;

import java.util.Random;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import org.ejml.ops.RandomMatrices;

/**
 * This class can be used for performing a random orthogonal transformation on a given vector.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class RandomRotation {

	/**
	 * This is the random rotation matrix.
	 */
	private DenseMatrix64F randomMatrix;

	/**
	 * Constructor that initializes a random rotation matrix using the EJML library.
	 * 
	 * @param seed
	 *            The seed used for generating the random rotation matrix
	 * @param dim
	 *            The dimensionality of the vectors that we want to randomly rotate
	 */
	public RandomRotation(int seed, int dim) {
		Random rand = new Random(seed);
		// create a random rotation matrix
		randomMatrix = new DenseMatrix64F(dim, dim);
		randomMatrix = RandomMatrices.createOrthogonal(dim, dim, rand);
	}

	/**
	 * Randomly rotates a vector using the random rotation matrix that was created in the constructor.
	 * 
	 * @param vector
	 *            The initial vector
	 * @return The randomly rotated vector
	 */
	public double[] rotate(double[] vector) {
		DenseMatrix64F transformed = new DenseMatrix64F(1, vector.length);
		DenseMatrix64F original = DenseMatrix64F.wrap(1, vector.length, vector);
		CommonOps.mult(original, randomMatrix, transformed);
		return transformed.getData();
	}
}
