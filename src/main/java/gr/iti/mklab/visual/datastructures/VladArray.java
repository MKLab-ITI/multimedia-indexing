package gr.iti.mklab.visual.datastructures;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gr.iti.mklab.visual.utilities.Result;

import java.util.Arrays;

import org.ejml.alg.dense.mult.VectorVectorMult;
import org.ejml.data.DenseMatrix64F;


import com.aliasi.util.BoundedPriorityQueue;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.ForwardCursor;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * This class can be used for storing vectors and also for performing k-nearest neighbor queries.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class VladArray extends AbstractSearchStructure {

	/**
	 * The vectors are stored here. Note that we use a single double array for all vectors.
	 */
	private TDoubleArrayList vectorsList;

	/**
	 * The ids of the corresponding images are stored in this int array. This field is probably not needed
	 * since the position of a vector in the VladArray is the same as its id.
	 */
	private TIntArrayList idsList;

	/**
	 * Whether the index will be loaded in memory or not.
	 */
	private boolean loadIndexInMemory;

	/**
	 * Whether to use a disk ordered cursor or not. This setting changes how fast the index will be loaded in
	 * main memory.
	 */
	private boolean useDiskOrderedCursor = false;

	/**
	 * Berkeley db for persistence of the VladArray data structure
	 */
	private Database vladArrayBDB;

	/**
	 * The total time taken to update the persistent index.
	 */
	private long totalpersistentIndexUpdateTime;

	/**
	 * Advanced constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the vectors.
	 * @param loadCounter
	 *            The initial value of the loadCounter.
	 * @param maxNumVectors
	 *            The maximum number of vectors.
	 * @param BDBEnvHome
	 *            The BDB environment home directory.
	 * @param loadIndexInMemory
	 *            Whether to load the index in memory.
	 * @param countSizeOnLoad
	 *            Whether the loadCounter will be initialized by the size of the persistent store.
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens).
	 * @throws Exception
	 */
	public VladArray(int vectorLength, int loadCounter, int maxNumVectors, String BDBEnvHome, boolean loadIndexInMemory, boolean countSizeOnLoad, boolean readOnly) throws Exception {
		super(vectorLength, loadCounter, maxNumVectors, readOnly, countSizeOnLoad);
		this.loadIndexInMemory = loadIndexInMemory;

		createOrOpenBDBEnvAndDbs(BDBEnvHome);

		// configuration of the persistent index
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(transactional);
		dbConf.setAllowCreate(true); // db will be created if it does not exist
		vladArrayBDB = dbEnv.openDatabase(null, "vlad", dbConf); // create/open the db using config

		if (countSizeOnLoad) {// count and print the size of the db
			int persistentIndexSize = (int) vladArrayBDB.count();
			System.out.println("Persistent index size: " + persistentIndexSize);
			if (idToNameMappings != persistentIndexSize) {
				throw new Exception("Persistent index size: " + persistentIndexSize + " != mapping db size: " + idToNameMappings + " !");
			}
		}

		if (this.loadIndexInMemory) {// load the existing persistent index in memory
			loadIndexInMemory(useDiskOrderedCursor);
		}
	}

	/**
	 * Simple constructor.
	 * 
	 * @param vectorLength
	 * @param maxNumVectors
	 * @param BDBEnvHome
	 * @throws Exception
	 */
	public VladArray(int vectorLength, int maxNumVectors, String BDBEnvHome) throws Exception {
		this(vectorLength, 0, maxNumVectors, BDBEnvHome, true, true, false);
	}

	/**
	 * Append the vectors array with the given vector and the ids array with the given id. The id is assigned
	 * by the indexVector method and is equal to the current value of the load counter minus 1.
	 * 
	 * @param name
	 * @param vector
	 * @throws Exception
	 */
	protected void indexVectorInternal(int id, double[] vector, Transaction txn) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The dimensionality of the vector is wrong!");
		}
		// update the persistent index
		long start = System.currentTimeMillis();
		updatePersistentIndex(id, vector, txn);
		totalpersistentIndexUpdateTime += System.currentTimeMillis() - start;
		// update the ram-based index
		if (loadIndexInMemory) {
			idsList.add(id);
			vectorsList.add(vector);
		}
	}

	/**
	 * Computes the k-nearest neighbors for the given query vector. The search is exhaustive but includes some
	 * optimizations which avoid many calculations.
	 * 
	 * @param k
	 *            the number of nearest neighbors to be returned
	 * @param queryVector
	 *            the query vector
	 * 
	 * @return the ids of the k nearest vectors
	 * @throws Exception
	 */
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] queryVector) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot execute query because the index is not loaded in memory!");
		}
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// Apply the appropriate transformation to the query vector, i.e. the
		// same transformation as the one applied to the indexed vectors.
		// If the vector is already normalized, this step might slightly change the vector.
		// if (normalize) {
		// queryVector = Normalization.normalizeL2(queryVector);
		// }

		double lowest = Double.MAX_VALUE;
		int counter = 0;
		// for (int i = 0; i < loadCounter; i++) {
		// for (int i = loadCounter - 1; i > 0; i--) {
		for (int i = 0; i < (vectorsList.size() / vectorLength); i++) {
			// for (int i = (vectorsList.size() / vectorLength) - 1; i > 0; i--) {
			counter++;
			boolean skip = false;
			int id = idsList.getQuick(i);
			int startIndex = i * vectorLength;
			double l2distance = 0;
			for (int j = 0; j < vectorLength; j++) {
				l2distance += (queryVector[j] - vectorsList.getQuick(startIndex + j)) * (queryVector[j] - vectorsList.getQuick(startIndex + j));
				// l2distance += Math.pow((queryVector[j] - vectorsList.getQuick(startIndex + j)),
				// (queryVector[j] - vectorsList.getQuick(startIndex + j)));

				if (l2distance > lowest) {
					skip = true;
					break;
				}
			}
			if (skip) {
				continue;
			}

			nn.offer(new Result(id, l2distance));
			if (counter > k) {
				lowest = nn.last().getDistance();
			}
		}

		return nn;
	}

	/**
	 * Computes the k-nearest neighbors using the inner product.
	 * 
	 * @throws Exception
	 */
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternalCosine(int k, double[] queryVector) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot execute query because the index is not loaded in memory!");
		}
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// Apply the appropriate transformation to the query vector, i.e. the
		// same transformation as the one applied to the indexed vectors.
		// If the vector is already normalized, this step might slightly change the vector.
		// if (normalize) {
		// queryVector = Normalization.normalizeL2(queryVector);
		// }

		for (int i = 0; i < (vectorsList.size() / vectorLength); i++) {
			int id = idsList.getQuick(i);
			int startIndex = i * vectorLength;
			double cosineSimilariry = 0;
			for (int j = 0; j < vectorLength; j++) {
				cosineSimilariry += queryVector[j] * vectorsList.getQuick(startIndex + j);
			}
			nn.offer(new Result(id, -cosineSimilariry));
		}

		return nn;
	}

	/**
	 * Computes the k-nearest neighbors using the inner product.
	 * 
	 * @throws Exception
	 */
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternalCosineVectorized(int k, double[] queryVector) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot execute query because the index is not loaded in memory!");
		}
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		// Apply the appropriate transformation to the query vector, i.e. the
		// same transformation as the one applied to the indexed vectors.
		// If the vector is already normalized, this step might slightly change the vector.
		// if (normalize) {
		// queryVector = Normalization.normalizeL2(queryVector);
		// }
		DenseMatrix64F qvec = DenseMatrix64F.wrap(vectorLength, 1, queryVector);

		for (int i = 0; i < (vectorsList.size() / vectorLength); i++) {
			int id = idsList.getQuick(i);
			double[] vector = new double[vectorLength];
			int startIndex = i * vectorLength;
			for (int j = 0; j < vectorLength; j++) {
				vector[j] = vectorsList.getQuick(startIndex + j);
			}
			DenseMatrix64F vec = DenseMatrix64F.wrap(vectorLength, 1, vector);
			double cosineSimilariry = VectorVectorMult.innerProd(vec, qvec);
			nn.offer(new Result(id, -cosineSimilariry));
		}

		return nn;
	}

	// /**
	// * Write a VladArray in a text or binary file.
	// *
	// * @param fileName
	// * @param binary
	// * @throws Exception
	// */
	// public void writeArrayToFile(String fileName, boolean binary) throws Exception {
	// BufferedWriter outFull = null;
	// DataOutputStream outFullBinary = null;
	// if (binary) {
	// outFullBinary = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
	// for (int i = 0; i < loadCounter; i++) {
	// outFullBinary.writeUTF(readMapping(ids[i]));
	// for (int j = 0; j < vectorLength; j++) {
	// outFullBinary.writeDouble(vectors[i * vectorLength + j]);
	// }
	// outFullBinary.flush();
	// }
	// } else {
	// outFull = new BufferedWriter(new FileWriter(fileName));
	// for (int i = 0; i < loadCounter; i++) {
	// outFull.write(readMapping(ids[i]) + " ");
	// for (int j = 0; j < vectorLength - 1; j++) {
	// outFull.write(vectors[i * vectorLength + j] + " ");
	// }
	// outFull.write(vectors[i * vectorLength + vectorLength - 1] + "\n");
	// outFull.flush();
	// }
	// }
	//
	// }

	@Override
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int internalId) throws Exception {
		double[] queryVector = getVector(internalId);
		return computeNearestNeighborsInternalCosine(k, queryVector);
	}

	/**
	 * Loads the persistent index in memory.
	 * 
	 * @param diskOrdered
	 *            whether to use a diskOrderedCursor
	 * @throws Exception
	 */
	private void loadIndexInMemory(boolean diskOrdered) throws Exception {
		long start = System.currentTimeMillis();
		System.out.println("Loading persistent index in memory.");
		// create the memory objects with the appropriate initial size
		idsList = new TIntArrayList(maxNumVectors);
		vectorsList = new TDoubleArrayList(maxNumVectors * vectorLength);

		DatabaseEntry foundKey = new DatabaseEntry();
		DatabaseEntry foundData = new DatabaseEntry();

		// standard cursor
		ForwardCursor cursor = null;
		if (diskOrdered) { // disk ordered cursor
			DiskOrderedCursorConfig docc = new DiskOrderedCursorConfig();
			cursor = vladArrayBDB.openCursor(docc);
		} else {
			cursor = vladArrayBDB.openCursor(null, null);
		}
		// To iterate, just call getNext() until the last database record has been read. All cursor operations
		// return an OperationStatus, so just read until we no longer see OperationStatus.SUCCESS
		int counter = 0;
		while (cursor.getNext(foundKey, foundData, null) == OperationStatus.SUCCESS && counter < maxNumVectors) {
			int id = IntegerBinding.entryToInt(foundKey);
			TupleInput input = TupleBinding.entryToInput(foundData);
			double[] vector = new double[vectorLength];
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = input.readDouble();
			}
			// print the last 10 indexed vectors
			// if (counter >= 0 && (counter >= vladArrayBDB.count() - 10 || counter == 0)) {
			// System.out.print("id : " + id + " , size : " + vector.length + " | vector : ");
			// for (int i = 0; i < 5; i++) {
			// System.out.print(vector[i] + " ");
			// }
			// System.out.println(" ...");
			// }
			// update ram based index
			vectorsList.add(vector);
			idsList.add(id);

			counter++;
			if (counter % 1000 == 0) {
				System.out.println(counter + " images loaded in memory!");
			}
		}
		// dcursor.close();
		cursor.close();
		long end = System.currentTimeMillis();
		System.out.println(counter + " images loaded in " + (end - start) + " ms!");
	}

	/**
	 * Updates the persisent vlad index.
	 * 
	 * @param id
	 * @param vector
	 * @param txn
	 */
	private void updatePersistentIndex(int id, double[] vector, Transaction txn) {
		// write id, vector
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < vectorLength; i++) {
			output.writeDouble(vector[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		vladArrayBDB.put(txn, key, data);
	}

	/**
	 * Returns the k-th vector, either from the ram-based or from the disk-based index.
	 * 
	 * @param k
	 *            index of the vector to be returned (starts from 0)
	 * @return
	 * @throws Exception
	 */
	public double[] getVector(int k) throws Exception {
		if (k > loadCounter) {
			throw new Exception("k is larger than the number of vectors indexed in memory!");
		}
		double[] vector = new double[vectorLength];
		if (loadIndexInMemory) {
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = vectorsList.getQuick(k * vectorLength + i);
			}
		} else {
			// get the kth vector form the BDB structure
			DatabaseEntry key = new DatabaseEntry();
			IntegerBinding.intToEntry(k, key);
			DatabaseEntry foundData = new DatabaseEntry();
			vladArrayBDB.get(null, key, foundData, null);
			TupleInput input = TupleBinding.entryToInput(foundData);
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = input.readDouble();
			}
		}

		return vector;
	}

	/**
	 * Returns the k-th id in the ids array
	 * 
	 * @param k
	 *            index of the id to be returned (starts from 0)
	 * 
	 * @return
	 * @throws Exception
	 */
	public int getVectorId(int k) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot get vector id because the index is not loaded in memory!");
		}
		if (k > loadCounter) {
			throw new Exception("k is larger than the number of indexed vectors");
		}
		return idsList.getQuick(k);
	}

	@Override
	public void closeInternal() {
		vladArrayBDB.close();
	}

	@Override
	protected void outputIndexingTimesInternal() {
		System.out.println((double) totalpersistentIndexUpdateTime / loadCounter + " ms => persistentIndexUpdateTime");

	}

	@Override
	protected void syncDBinternal() {
		vladArrayBDB.sync();
	}

	@Override
	protected boolean deleteVectorInternal(int id) {
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		vladArrayBDB.delete(null, key);
		return true;
	}

	public boolean equals(Object vlad2) {
		try {
			if (this.vectorLength != ((VladArray) vlad2).vectorLength) {
				System.out.println("vector lengths are different!");
				return false;
			}
			for (int i = 0; i < loadCounter; i++) {
				int id1 = this.getVectorId(i);
				int id2 = ((VladArray) vlad2).getVectorId(i);
				if (id1 != id2) {
					System.out.println("ids at position " + i + " are different!");
					return false;
				}
				String name1 = this.getExternalId(id1, null);
				String name2 = ((VladArray) vlad2).getExternalId(id2, null);
				if (!name1.equals(name2)) {
					System.out.println("names at position " + i + " are different!");
					return false;
				}
				double[] vector1 = this.getVector(i);
				double[] vector2 = ((VladArray) vlad2).getVector(i);
				for (int j = 0; j < vector2.length; j++) {
					if (vector1[j] != vector2[j]) {
						System.out.println("vectors at position " + i + " are different!");
						System.out.println(name1);
						System.out.println(Arrays.toString(vector1));
						System.out.println(Arrays.toString(vector2));
						// return false;
						break;
					}
				}
				if (i % 100 == 0) {
					System.out.println("vectors at position " + i + " are equal!");
				}
			}
			return true;

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Checks equality of two VladArrays
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {

		String BDBenvCollection1 = "C:/Users/lef/Desktop/ITI/data/Holidays/index/BDB_8192_2x64_1/";
		String BDBenvCollection2 = "C:/Users/lef/Desktop/ITI/data/Holidays/index/BDB_8192_2x64_2/";
		VladArray index1 = new VladArray(8192, 0, 1491, BDBenvCollection1, true, true, true);
		VladArray index2 = new VladArray(8192, 0, 1491, BDBenvCollection2, true, true, true);
		if (index1.equals(index2)) {
			System.out.println("Indices are equal!");
		} else {
			System.out.println("Indices are different!");
		}
	}
}