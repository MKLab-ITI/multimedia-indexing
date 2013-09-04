package gr.iti.mklab.visual.utilities;

import java.util.Random;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import org.ejml.ops.RandomMatrices;

/**
 * This class can be used for performing a random orthogonal transformation of a given vector.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class RandomTransformation {

	private Random rand;

	private DenseMatrix64F randomMatrix;

	public RandomTransformation(int seed, int dim) {
		rand = new Random(seed);
		// == create a random matrix ==
		randomMatrix = new DenseMatrix64F(dim, dim);
		randomMatrix = RandomMatrices.createOrthogonal(dim, dim, rand);

		// normalize column-wise
		// if (normalize) {
		// for (int i = 0; i < randomMatrix.numCols; i++) {
		// // get the collumn and compute the norm
		// double colnorm = 0;
		// for (int j = 0; j < randomMatrix.numRows; j++) {
		// colnorm += randomMatrix.get(j, i) * randomMatrix.get(j, i);
		// }
		// colnorm /= Math.sqrt(colnorm);
		// for (int j = 0; j < randomMatrix.numRows; j++) {
		// randomMatrix.set(j, i, randomMatrix.get(j, i) / colnorm);
		// }
		// }
		// CommonOps.transpose(randomMatrix);
		// // normalize row-wise
		// for (int i = 0; i < randomMatrix.numCols; i++) {
		// // get the collumn and compute the norm
		// double colnorm = 0;
		// for (int j = 0; j < randomMatrix.numRows; j++) {
		// colnorm += randomMatrix.get(j, i) * randomMatrix.get(j, i);
		// }
		// colnorm /= Math.sqrt(colnorm);
		// for (int j = 0; j < randomMatrix.numRows; j++) {
		// randomMatrix.set(j, i, randomMatrix.get(j, i) / colnorm);
		// }
		// }
		// CommonOps.transpose(randomMatrix);
		// }
	}

	public double[] transform(double[] vector) {
		DenseMatrix64F transformed = new DenseMatrix64F(1, vector.length);
		DenseMatrix64F original = DenseMatrix64F.wrap(1, vector.length, vector);
		CommonOps.mult(original, randomMatrix, transformed);
		return transformed.getData();
	}
}
