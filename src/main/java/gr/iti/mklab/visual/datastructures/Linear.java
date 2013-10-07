package gr.iti.mklab.visual.datastructures;

import gnu.trove.list.array.TDoubleArrayList;
import gr.iti.mklab.visual.utilities.Result;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

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

/**
 * This class is used for indexing vectors and performing k-nearest neighbor queries with exhaustive linear
 * search.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Linear extends AbstractSearchStructure {

	/**
	 * The vectors are stored in this field. Note that we use a single TDoubleArrayList for all vectors.
	 */
	private TDoubleArrayList vectorsList;

	/**
	 * Whether the index will be loaded in memory. We can avoid loading the index in memory when we only want
	 * to perform indexing.
	 */
	private boolean loadIndexInMemory;

	/**
	 * Whether to use a disk ordered cursor or not. This setting changes how fast the index will be loaded in
	 * main memory.
	 */
	public final boolean useDiskOrderedCursor = false;

	/**
	 * BDB store for persistent storage of the linear index.
	 */
	private Database iidToVectorDB;

	/**
	 * Advanced constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 * @param BDBEnvHome
	 *            The BDB environment home directory
	 * @param loadIndexInMemory
	 *            Whether to load the index in memory, we can avoid loading the index in memory when we only
	 *            want to perform indexing
	 * @param countSizeOnLoad
	 *            Whether the load counter will be initialized by the size of the persistent store
	 * @param loadCounter
	 *            The initial value of the load counter
	 * @throws Exception
	 */
	public Linear(int vectorLength, int maxNumVectors, boolean readOnly, String BDBEnvHome, boolean loadIndexInMemory, boolean countSizeOnLoad, int loadCounter) throws Exception {
		super(vectorLength, maxNumVectors, readOnly, countSizeOnLoad, loadCounter);
		createOrOpenBDBEnvAndDbs(BDBEnvHome);
		this.loadIndexInMemory = loadIndexInMemory;
		// configuration of the persistent index
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(transactional);
		dbConf.setAllowCreate(true); // db will be created if it does not exist
		iidToVectorDB = dbEnv.openDatabase(null, "vlad", dbConf); // create/open the db using config

		if (loadIndexInMemory) {// load the existing persistent index in memory
			// create the memory objects with the appropriate initial size
			vectorsList = new TDoubleArrayList(maxNumVectors * vectorLength);
			loadIndexInMemory();
		}
	}

	/**
	 * Simple constructor.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 * @param BDBEnvHome
	 *            The BDB environment home directory
	 * @throws Exception
	 */
	public Linear(int vectorLength, int maxNumVectors, boolean readOnly, String BDBEnvHome) throws Exception {
		this(vectorLength, maxNumVectors, readOnly, BDBEnvHome, true, true, 0);
	}

	/**
	 * Append the vectors array with the given vector. The iid of this vector will be equal to the current
	 * value of the loadCounter.
	 * 
	 * @param vector
	 *            The vector to be indexed
	 * @throws Exception
	 *             If the vector's dimensionality is different from vectorLength
	 */
	protected void indexVectorInternal(double[] vector) throws Exception {
		if (vector.length != vectorLength) {
			throw new Exception("The dimensionality of the vector is wrong!");
		}
		// append the persistent index
		appendPersistentIndex(vector);
		// append the ram-based index
		if (loadIndexInMemory) {
			vectorsList.add(vector);
		}
	}

	/**
	 * Computes the k-nearest neighbors of the given query vector. The search is exhaustive but includes some
	 * optimizations that make it faster, especially for high dimensional vectors.
	 * 
	 * @param k
	 *            The number of nearest neighbors to be returned
	 * @param queryVector
	 *            The query vector
	 * 
	 * @return A bounded priority queue of Result objects, which contains the k nearest neighbors along with
	 *         their iids and distances from the query vector, ordered by lowest distance.
	 * @throws Exception
	 *             If the index is not loaded in memory
	 * 
	 */
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] queryVector) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot execute query because the index is not loaded in memory!");
		}
		BoundedPriorityQueue<Result> nn = new BoundedPriorityQueue<Result>(new Result(), k);

		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < (vectorsList.size() / vectorLength); i++) {
			boolean skip = false;
			int startIndex = i * vectorLength;
			double l2distance = 0;
			for (int j = 0; j < vectorLength; j++) {
				l2distance += (queryVector[j] - vectorsList.getQuick(startIndex + j)) * (queryVector[j] - vectorsList.getQuick(startIndex + j));
				if (l2distance > lowest) {
					skip = true;
					break;
				}
			}
			if (!skip) {
				nn.offer(new Result(i, l2distance));
				if (i >= k) {
					lowest = nn.last().getDistance();
				}
			}
		}
		return nn;
	}

	/**
	 * Computes the k-nearest neighbors of the vector with the given internal id. The search is exhaustive but
	 * includes some optimizations that make it faster, especially for high dimensional vectors.
	 * 
	 * @param k
	 *            The number of nearest neighbors to be returned
	 * @param queryVector
	 *            The internal id of the query vector
	 * 
	 * @return A bounded priority queue of Result objects, which contains the k nearest neighbors along with
	 *         their iids and distances from the vector with the given internal id, ordered by lowest
	 *         distance.
	 * @throws Exception
	 *             If the index is not loaded in memory
	 * 
	 */
	protected BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int iid) throws Exception {
		double[] queryVector = getVector(iid); // get the vector with this internal id
		return computeNearestNeighborsInternal(k, queryVector);
	}

	/**
	 * Loads the persistent index in memory.
	 * 
	 * @throws Exception
	 */
	private void loadIndexInMemory() throws Exception {
		long start = System.currentTimeMillis();
		System.out.println("Loading persistent index in memory.");

		DatabaseEntry foundKey = new DatabaseEntry();
		DatabaseEntry foundData = new DatabaseEntry();

		ForwardCursor cursor = null;
		if (useDiskOrderedCursor) { // disk ordered cursor
			DiskOrderedCursorConfig docc = new DiskOrderedCursorConfig();
			cursor = iidToVectorDB.openCursor(docc);
		} else {
			cursor = iidToVectorDB.openCursor(null, null);
		}

		int counter = 0;
		while (cursor.getNext(foundKey, foundData, null) == OperationStatus.SUCCESS && counter < maxNumVectors) {
			TupleInput input = TupleBinding.entryToInput(foundData);
			double[] vector = new double[vectorLength];
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = input.readDouble();
			}
			// update ram based index
			vectorsList.add(vector);
			counter++;
			if (counter % 1000 == 0) {
				System.out.println(counter + " vectors loaded in memory!");
			}
		}
		cursor.close();
		long end = System.currentTimeMillis();
		System.out.println(counter + " vectors loaded in " + (end - start) + " ms!");
	}

	/**
	 * Appends the persistent index with the given vector.
	 * 
	 * @param vector
	 *            The vector
	 */
	private void appendPersistentIndex(double[] vector) {
		TupleOutput output = new TupleOutput();
		for (int i = 0; i < vectorLength; i++) {
			output.writeDouble(vector[i]);
		}
		DatabaseEntry data = new DatabaseEntry();
		TupleBinding.outputToEntry(output, data);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(loadCounter, key);
		iidToVectorDB.put(null, key, data);
	}

	/**
	 * Returns the vector which was assigned the given internal id or null if the internal id does not exist.
	 * The vector is taken either from the ram-based (if loadIndexInMemory is true) or from the disk-based
	 * index.
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @return The vector with the given internal id or null if the internal id does not exist
	 */
	public double[] getVector(int iid) {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return null;
		}
		double[] vector = new double[vectorLength];
		if (loadIndexInMemory) {
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = vectorsList.getQuick(iid * vectorLength + i);
			}
		} else {
			// get the vector from the BDB structure
			DatabaseEntry key = new DatabaseEntry();
			IntegerBinding.intToEntry(iid, key);
			DatabaseEntry foundData = new DatabaseEntry();
			if (iidToVectorDB.get(null, key, foundData, null) == OperationStatus.SUCCESS) {
				TupleInput input = TupleBinding.entryToInput(foundData);
				for (int i = 0; i < vectorLength; i++) {
					vector[i] = input.readDouble();
				}
			} else {
				System.out.println("Internal id " + iid + " is in range but vector was not found..");
				System.out.println("Index is probably corrupted");
				System.exit(0);
				return null;
			}
		}
		return vector;
	}

	@Override
	protected void closeInternal() {
		iidToVectorDB.close();
	}

	@Override
	protected void outputIndexingTimesInternal() {

	}

	/**
	 * Writes all vectors in a csv formated file. The id goes first, followed by the vector.
	 * 
	 * @param fileName
	 *            Full path to the file
	 * @throws Exception
	 */
	public void toCSV(String fileName) throws Exception {
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(fileName)));
		for (int i = 0; i < loadCounter; i++) {
			String identifier = getId(i);
			double[] vector = getVector(i);
			out.write(identifier);
			for (int k = 0; k < vector.length; k++) {
				out.write("," + vector[k]);
			}
			out.write("\n");
			out.flush();
		}
		out.close();
	}

}