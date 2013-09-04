package gr.iti.mklab.visual.datastructures;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.jinstagram.entity.users.feed.MediaFeedData;


import com.aliasi.util.BoundedPriorityQueue;
import com.javadocmd.simplelatlng.LatLng;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentNotFoundException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;

import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.Result;

/**
 * This abstract class abstracts the bdb environment creation and name to id mapping operations from the
 * actual indexing structures. In the future we can support multi-threaded indexing and querying.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public abstract class AbstractSearchStructure {

	/**
	 * The number of threads to use during indexing. Currently not used.
	 */
	protected int numIndexingThreads;

	/**
	 * The rate at which the disk-based indexed should be updated. Currently, this field is not used, the
	 * index is updated for every new vector.
	 */
	protected int syncRate = 100;

	/**
	 * Berkeley db holding name to id mappings, required during indexing to fast check if a name is already
	 * indexed.
	 */
	protected Database nameToIdBDB;

	/**
	 * Berkeley db holding id to name mappings, required for name look-up during nearest neighbor search.
	 */
	protected Database idToNameBDB;

	/**
	 * Berkeley db holding id to lognitude and latidute mappings, required for geolocation look-up during
	 * nearest neighbor search.
	 */
	protected Database idToGeolocationBDB;

	/**
	 * BDB store holding id to metadata mappings, required for metadata look-up during nearest neighbor
	 * search.
	 */
	protected EntityStore nameToMetadataBDB;

	/**
	 * The database environment. Access to this field is needed by specific data structures that implement
	 * persistence. If this field is null it means that persistence is not implemented.
	 */
	protected Environment dbEnv;

	/**
	 * Whether the environment will be transactional. For more information on what this means, refer to the
	 * BDB documentation.
	 */
	protected final boolean transactional = true;

	/**
	 * The percentage of the total memory given to the program that will be used as the BDB cache.
	 */
	protected final int cachePercent = 1;

	/**
	 * The total memory given to the program that will be used as the BDB cache.
	 */
	//protected final int cacheSize = 1024 * 1024 * 500;
	protected final int cacheSize = 1024 * 1024 * 100;

	
	/**
	 * Whether to create/load metadata dbs.
	 */
	protected final boolean useMetaData = false;

	/**
	 * Counts the number of persisted id to name mappings. This value can be different from loadCounter when
	 * maxNumVectors is smaller than the number of persisted vectors.
	 */
	protected int idToNameMappings;

	/**
	 * The length of the raw vectors being indexed.
	 */
	protected int vectorLength;

	/**
	 * The maximum number of vectors that can be indexed. An exception is thrown when this number is reached.
	 */
	protected final int maxNumVectors;

	/**
	 * Keeps track of the total number of indexed vectors, acts as an auto-increment primary key field.
	 */
	protected int loadCounter;

	/**
	 * Average time taken to create a name to id and the reverse mapping.
	 */
	private long totalNameMappingTime;

	/**
	 * Average time taken for internal vector indexing operations.
	 */
	private long totalInternalVectorIndexingTime;

	/**
	 * Average total time taken to index a vector.
	 */
	private long totalVectorIndexingTime;

	/**
	 * Whether the load counter should be initialized when the database is loaded by counting the id-name
	 * mappings. This count incurs a large cost when loading very large databases and can be set to false for
	 * efficiency reasons.
	 */
	protected boolean countSizeOnLoad;

	/**
	 * Whether the db should open only for read access.
	 */
	protected boolean readOnly;

	/**
	 * Returns the current value of the loadCounter.
	 * 
	 * @return
	 */
	public int getLoadCounter() {
		return loadCounter;
	}

	/**
	 * Returns the number of loaded metadata
	 * 
	 * @return
	 */
	public int getMetadataCounter() {
		int nameToMetadataMappings = (int) nameToMetadataBDB.getPrimaryIndex(String.class, MediaFeedData.class).count();
		return nameToMetadataMappings;
	}

	/**
	 * This constructor can be used when we do not want persistence.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed.
	 * @param loadCounter
	 *            The initial value of the loadCounter.
	 * @param maxNumVectors
	 *            The maximum number of vectors which we allow to be indexed.
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens).
	 * @param countSizeOnLoad
	 *            Whether the loadCounter will be initialized by the size of the persistent store.
	 */
	public AbstractSearchStructure(int vectorLength, int loadCounter, int maxNumVectors, boolean readOnly, boolean countSizeOnLoad) {
		this.vectorLength = vectorLength;
		this.loadCounter = loadCounter;
		this.maxNumVectors = maxNumVectors;
		this.readOnly = readOnly;
		this.countSizeOnLoad = countSizeOnLoad;
	}

	/**
	 * This method creates or opens (if it already exists) the BDB environment and the BDB dbs.
	 * 
	 * @param BDBEnvHome
	 *            The directory where the BDB environment will be created.
	 * @throws Exception
	 */
	protected void createOrOpenBDBEnvAndDbs(String BDBEnvHome) throws Exception {
		createOrOpenBDBEnv(BDBEnvHome);
		createOrOpenBDBDbs();
	}

	/**
	 * This method creates and/or opens the BDB environment in the supplied directory. We can tune the
	 * configuration in this method for better efficiency / less persistence!
	 * 
	 * @param BDBEnvHome
	 *            The directory where the BDB environment will be created.
	 * @throws Exception
	 */
	private void createOrOpenBDBEnv(String BDBEnvHome) throws Exception {
		// create the BDBEnvHome directory if it does not exist
		File BDBEnvHomeDir = new File(BDBEnvHome);
		if (!BDBEnvHomeDir.isDirectory()) {
			boolean success = BDBEnvHomeDir.mkdir();
			if (success) {
				System.out.println(BDBEnvHome + " directory created.");
			}
		} else {
			System.out.println(BDBEnvHome + " directory exists.");
		}

		// configuration of the bdb environment, applies to all dbs in this environment
		EnvironmentConfig envConf = new EnvironmentConfig();
		envConf.setReadOnly(readOnly);
		envConf.setTransactional(transactional);
		envConf.setCacheSize(cacheSize);// alternative expression: envConf.setCachePercent(cachePercent);
		envConf.setAllowCreate(false); // initially we do not allow create
		// Instantiate the Environment. This opens it and also possibly creates it.
		try {
			dbEnv = new Environment(BDBEnvHomeDir, envConf);
			System.out.println("An existing BDB environment was found.");
		} catch (EnvironmentNotFoundException e) {
			envConf.setAllowCreate(true);
			dbEnv = new Environment(BDBEnvHomeDir, envConf);
			System.out.println("A new BDB environment was created.");
		}

		// printing information about the BDB environment
		System.out.println("== BDB environment configuration ===");
		System.out.println(dbEnv.getConfig());
		System.out.println("== BDB environment database names ===");
		List<String> dbNames = dbEnv.getDatabaseNames();
		for (String dbName : dbNames) {
			System.out.println(dbName);
		}
		System.out.println("");
	}

	/**
	 * This method creates and/or opens the BDB dbs.
	 * 
	 * @throws Exception
	 */
	private void createOrOpenBDBDbs() throws Exception {
		// configuration for the mapping dbs
		DatabaseConfig dbConf = new DatabaseConfig();
		dbConf.setReadOnly(readOnly);
		dbConf.setTransactional(transactional);
		dbConf.setAllowCreate(true); // db will be created if it does not exist
		// create/open mapping dbs using config
		idToNameBDB = dbEnv.openDatabase(null, "idToName", dbConf);
		nameToIdBDB = dbEnv.openDatabase(null, "nameToId", dbConf);

		// if countSizeOnLoad is true the mappings are counted and the loadCounter is re-initialized
		if (countSizeOnLoad) {
			idToNameMappings = (int) idToNameBDB.count();
			loadCounter = Math.min(idToNameMappings, maxNumVectors);
			int nameToIdBDBMappings = (int) nameToIdBDB.count();
			System.out.println("Id to name mappings found: " + idToNameMappings);
			System.out.println("Name to id mappings found: " + nameToIdBDBMappings);
			if (idToNameMappings != nameToIdBDBMappings) {
				throw new Exception("Mapping dbs have different sizes!");
			}
		}

		if (useMetaData) {
			// create/open geolocation db using config
			idToGeolocationBDB = dbEnv.openDatabase(null, "idToGeolocation", dbConf);
			// configuration of the entity store
			StoreConfig storeConfig = new StoreConfig();
			storeConfig.setAllowCreate(true);
			nameToMetadataBDB = new EntityStore(dbEnv, "idToMetadata", storeConfig);

			int idToGeolocationMappings = (int) idToGeolocationBDB.count();
			int nameToMetadataMappings = (int) nameToMetadataBDB.getPrimaryIndex(String.class, MediaFeedData.class).count();

			System.out.println("Id to geolocation mappings found: " + idToGeolocationMappings);
			System.out.println("Name to metadata mappings found: " + nameToMetadataMappings);
		}

	}

	/**
	 * This method closes the BDB environment and databases!
	 * 
	 * @throws Exception
	 */
	public void close() throws Exception {
		if (dbEnv != null) {
			try {
				idToNameBDB.close();
				nameToIdBDB.close();
				if (useMetaData) {
					idToGeolocationBDB.close();
					nameToMetadataBDB.close();
				}
				closeInternal();
				dbEnv.close();
			} catch (DatabaseException dbe) {
				System.err.println("Error closing MyDbEnv: " + dbe.toString());
				System.exit(-1);
			}
		} else {
			throw new Exception("BDB environment is null!");
		}
	}

	/**
	 * Each subclass should implement this method to close the BDB databases that it uses.
	 */
	protected abstract void closeInternal();

	/**
	 * Checks if the vector with the given name is already indexed. This method is useful to avoid re-indexing
	 * the same vector. Its convention is that if the given name is already in nameToIdBDB, then the image is
	 * indexed in all other structures e.g. IdToNameBDB. The rest of the checks are avoided for better
	 * efficiency.
	 * 
	 * @param name
	 *            The name the vector to be indexed.
	 * @param txn
	 *            A transaction handle.
	 * @return true if the given name is already indexed
	 */
	public boolean isIndexed(String name, Transaction txn) {
		DatabaseEntry key = new DatabaseEntry();
		StringBinding.stringToEntry(name, key);
		DatabaseEntry data = new DatabaseEntry();
		// check if the name already exists in name to id database
		if ((nameToIdBDB.get(txn, key, data, null) == OperationStatus.SUCCESS)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method returns the internal id assigned in the image with the given name or other external unique
	 * identifier.
	 * 
	 * @param name
	 *            The name or other external unique identifier of the image.
	 * @param txn
	 *            A transaction handle.
	 * 
	 * @return The internal id assigned to this image.
	 * @throws Exception
	 */
	public int getInternalId(String name, Transaction txn) throws Exception {
		DatabaseEntry key = new DatabaseEntry();
		StringBinding.stringToEntry(name, key);
		DatabaseEntry data = new DatabaseEntry();
		// check if the name already exists in name to id database
		if ((nameToIdBDB.get(txn, key, data, null) == OperationStatus.SUCCESS)) {
			return IntegerBinding.entryToInt(data);
		} else {
			throw new Exception("Couldn't find image: " + name);
		}
	}

	/**
	 * This method is used to create a persistent mapping between a given id and name. Should be called every
	 * time that a new vector is going to be indexed.
	 * 
	 * @param id
	 *            The assigned id.
	 * @param name
	 *            The name.
	 * @param txn
	 *            A transaction handle.
	 */
	private void createMapping(int id, String name, Transaction txn) {
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		StringBinding.stringToEntry(name, data);
		idToNameBDB.put(txn, key, data); // required during name look-up
		nameToIdBDB.put(txn, data, key); // required during indexing

	}

	/**
	 * This method is used to set the geolocation of an indexed image.
	 * 
	 * @param name
	 * @param latitude
	 * @param longitude
	 * @param txn
	 * @return
	 */
	public boolean setGeolocation(String name, double latitude, double longitude, Transaction txn) {
		int id;
		try {
			id = getInternalId(name, txn);
		} catch (Exception e) {
			return false;
		}
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry();

		IntegerBinding.intToEntry(id, key);
		TupleOutput output = new TupleOutput();
		output.writeDouble(latitude);
		output.writeDouble(longitude);
		TupleBinding.outputToEntry(output, data);

		idToGeolocationBDB.put(txn, key, data);

		return true;
	}

	/**
	 * 
	 * @param id
	 * @param metaData
	 * @param txn
	 * @return
	 */
	public boolean setMetadata(String id, MediaFeedData metaData, Transaction txn) {
		PrimaryIndex<String, MediaFeedData> primaryIndex = nameToMetadataBDB.getPrimaryIndex(String.class, MediaFeedData.class);
		MediaFeedData entity = primaryIndex.put(txn, metaData);
		if (entity == null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @param id
	 * @param txn
	 * @return
	 * @throws Exception
	 */
	public MediaFeedData getMetadata(String id, Transaction txn) throws Exception {
		PrimaryIndex<String, MediaFeedData> primaryIndex = nameToMetadataBDB.getPrimaryIndex(String.class, MediaFeedData.class);
		return primaryIndex.get(txn, id, null);
	}

	/**
	 * Checks if the given image is assigned a geotag.
	 * 
	 * @param name
	 * @param txn
	 * @return
	 * @throws Exception
	 */
	public boolean isGeolocated(String name, Transaction txn) throws Exception {
		int id = getInternalId(name, txn);
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		DatabaseEntry data = new DatabaseEntry();
		// check if the name already exists in name to id database
		if ((idToGeolocationBDB.get(txn, key, data, null) == OperationStatus.SUCCESS)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @param id
	 * @param txn
	 * @return
	 * @throws Exception
	 */
	public LatLng getGeolocation(int id, Transaction txn) throws Exception {
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		DatabaseEntry data = new DatabaseEntry();
		if ((idToGeolocationBDB.get(txn, key, data, null) == OperationStatus.SUCCESS)) {
			TupleInput input = TupleBinding.entryToInput(data);
			double latitude = input.readDouble();
			double longitude = input.readDouble();
			LatLng geolocation = new LatLng(latitude, longitude);
			return geolocation;
		} else {
			// throw new Exception("Couldn't find id: " + id);
			return null;
		}
	}

	/**
	 * This method returns the name or other external unique identifier of the image which was assigned the
	 * given internal id.
	 * 
	 * @param id
	 *            The id of the image.
	 * @param txn
	 *            A transaction handle.
	 * 
	 * @return The name or other external unique identifier mapped to the given id.
	 * @throws Exception
	 */
	public String getExternalId(int id, Transaction txn) throws Exception {
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(id, key);
		DatabaseEntry data = new DatabaseEntry();
		if ((idToNameBDB.get(txn, key, data, null) == OperationStatus.SUCCESS)) {
			return StringBinding.entryToString(data);
		} else {
			throw new Exception("Couldn't find");
		}
	}

	/**
	 * This method returns a bounded priority queue of Result objects, corresponding to the k nearest
	 * neighbors' ids and their distances from the query vector, ordered by lowest distance. A subclass
	 * specific implementation.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return.
	 * @param queryVector
	 *            The query vector.
	 * @return A bounded priority queue of vector objects.
	 * @throws Exception
	 */
	protected abstract BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, double[] queryVector) throws Exception;

	/**
	 * This method returns a bounded priority queue of Result objects, containing the k nearest neighbors' ids
	 * and their distances from the image with the given id, ordered by lowest distance. Subclass specific
	 * implementation.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return.
	 * @param internalId
	 *            The internal id of the vector.
	 * @return A bounded priority queue of Result objects.
	 * @throws Exception
	 */
	protected abstract BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int internalId) throws Exception;

	/**
	 * This method returns an array of Result objects, corresponding to the k nearest neighbors' ids, names
	 * and their distances from the query vector, ordered by lowest distance. The methods calls
	 * computeNearestNeighborsInternal and then reads the mappings of ids to names.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return.
	 * @param queryVector
	 *            The query vector.
	 * @return An array of Result objects.
	 * @throws Exception
	 */
	public Answer computeNearestNeighbors(int k, double[] queryVector) throws Exception {
		long start = System.nanoTime();
		BoundedPriorityQueue<Result> nnQueue = computeNearestNeighborsInternal(k, queryVector);
		long indexSearchTime = System.nanoTime() - start;

		start = System.nanoTime();
		Result[] nnArray = new Result[nnQueue.size()];
		nnArray = nnQueue.toArray(nnArray);
		for (int i = 0; i < nnArray.length; i++) {
			int id = nnArray[i].getInternalId();
			String name = getExternalId(id, null);
			nnArray[i].setExternalId(name);
			if (useMetaData) {
				LatLng geolocation = getGeolocation(id, null);
				nnArray[i].setGeolocation(geolocation);
			}
		}
		long nameLookupTime = System.nanoTime() - start;

		return new Answer(nnArray, nameLookupTime, indexSearchTime);
	}

	/**
	 * This method returns a bounded priority queue of Result objects, containing the k nearest neighbors' ids
	 * and their distances from the image with the given id, ordered by lowest distance.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return.
	 * @param id
	 *            The id of the vector.
	 * @return An array of Result objects.
	 * @throws Exception
	 *             if the image with the given id is not indexed.
	 */
	public Result[] computeNearestNeighbors(int k, String id) throws Exception {
		int internalIdquery = getInternalId(id, null);

		BoundedPriorityQueue<Result> nnQueue = computeNearestNeighborsInternal(k, internalIdquery);
		// long start = System.nanoTime();
		Result[] nnArray = new Result[nnQueue.size()];
		nnArray = nnQueue.toArray(nnArray);

		for (int i = 0; i < nnArray.length; i++) {
			int internalId = nnArray[i].getInternalId();
			String externalId = getExternalId(internalId, null);
			nnArray[i].setExternalId(externalId);
			//LatLng geolocation = getGeolocation(internalId, null);
			//nnArray[i].setGeolocation(geolocation);
		}
		// System.out.println(" Name look-up time: " + (double) (System.nanoTime() - start) / 1000000);
		return nnArray;
	}

	/**
	 * Updates the index with the given vector. This is a synchronized method.
	 * 
	 * @param name
	 *            The name of the vector.
	 * @param vector
	 *            The vector.
	 * @return True if the vector is successfully indexed, false otherwise.
	 * @throws Exception
	 */
	public synchronized boolean indexVector(String name, double[] vector) throws Exception {
		long startIndexing = System.currentTimeMillis();
		// check if we can index more vectors
		if (loadCounter >= maxNumVectors) {
			System.out.println("Maximum index capacity reached, no more vectors can be indexed!");
			return false;
		}
		// check if name is already indexed
		if (isIndexed(name, null)) {
			System.out.println("Vector '" + name + "' already indexed!");
			return false;
		}
		// start a new transaction
		Transaction txn = dbEnv.beginTransaction(null, null);
		// do the indexing
		// persist id to name and the reverse mapping
		long startMapping = System.currentTimeMillis();
		createMapping(loadCounter, name, txn);
		totalNameMappingTime += System.currentTimeMillis() - startMapping;
		// method specific indexing
		long startInternalIndexing = System.currentTimeMillis();
		indexVectorInternal(loadCounter, vector, txn);
		totalInternalVectorIndexingTime += System.currentTimeMillis() - startInternalIndexing;
		// end the transaction
		txn.commit();
		// increase the loadCounter
		loadCounter++;
		// debug message
		if (loadCounter % syncRate == 0) {
			// syncDB();
			System.out.println(new Date() + " # indexed vectors: " + loadCounter);
		}
		// System.out.println("Vector '" + name + "' was indexed successfully!");
		totalVectorIndexingTime += System.currentTimeMillis() - startIndexing;
		return true;
	}

	/**
	 * This method should be implemented in all subclasses and do the operations required for indexing the
	 * given vector and id.
	 * 
	 * @param id
	 *            The id of the vector to be indexed.
	 * @param vector
	 *            The vector to be indexed.
	 * @param txn
	 *            A transaction handle.
	 * 
	 * @throws Exception
	 */
	protected abstract void indexVectorInternal(int id, double[] vector, Transaction txn) throws Exception;

	public synchronized boolean deleteVector(int id) {
		try {
			String externallId = getExternalId(id, null);

			DatabaseEntry key = new DatabaseEntry();
			IntegerBinding.intToEntry(id, key);
			idToNameBDB.delete(null, key);
			idToGeolocationBDB.delete(null, key);

			key = new DatabaseEntry();
			StringBinding.stringToEntry(externallId, key);
			nameToIdBDB.delete(null, key);

			deleteVectorInternal(id);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	protected abstract boolean deleteVectorInternal(int id);

	/**
	 * Can be used to sync the persistent index in a predefined sync rate. Currently not in use.
	 */
	public void syncDB() {
		idToNameBDB.sync();
		nameToIdBDB.sync();
		idToGeolocationBDB.sync();
		syncDBinternal();
	}

	/**
	 * Subclass specific sync.
	 */
	protected abstract void syncDBinternal();

	/**
	 * This method can be called to output indexing time measurements.
	 */
	public void outputIndexingTimes() {
		System.out.println((double) totalInternalVectorIndexingTime / loadCounter + " ms => indexingInternalTime");
		System.out.println((double) totalNameMappingTime / loadCounter + " ms => NameMappingTime");
		System.out.println((double) totalVectorIndexingTime / loadCounter + " ms => indexingTime");
		outputIndexingTimesInternal();
	}

	/**
	 * Index specific time measurements.
	 * 
	 * @param loadCounter
	 */
	protected abstract void outputIndexingTimesInternal();

	public void IndexVectorsFromFilesB(String databaseFile, String distractorsFile, int maxNumVectors, HashSet<String> querySet) throws Exception {
		int counter = 0;
		System.out.println("Indexing vectors from the database file: " + databaseFile);
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(databaseFile)));
		while (counter < maxNumVectors) {
			if (counter % 100 == 0) {
				System.out.print("+ ");
			}
			String path;
			try {
				path = in.readUTF();
			} catch (IOException e) {
				// reached the end of file so
				// break this loop
				break;
			}

			// when the size of the querySet is
			// null, then we read all the
			// vectors!
			if (querySet != null) {
				// else we read only the queries
				// which are not quantized
				if (!querySet.contains(path)) {
					for (int i = 0; i < vectorLength; i++) {
						// skip vectorLength
						// doubles
						in.readDouble();
					}
					continue; // we don't read the
					// full vector
				}
			}

			double[] vector = new double[vectorLength];
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = in.readDouble();
			}
			this.indexVector(path, vector);
			counter++;
		}
		in.close();
		int numDatabaseVectors = counter;
		System.out.println("\n" + numDatabaseVectors + " vectors indexed from the database file!");

		System.out.println("Indexing vectors from the distractors file: " + distractorsFile);
		in = new DataInputStream(new BufferedInputStream(new FileInputStream(distractorsFile)));
		while (counter < maxNumVectors) {
			if (counter % 100 == 0) {
				System.out.print("+ ");
			}
			String path;
			try {
				path = in.readUTF();
			} catch (IOException e) {
				// reached the end of file so
				// break this loop
				break;
			}
			double[] vector = new double[vectorLength];
			for (int i = 0; i < vectorLength; i++) {
				vector[i] = in.readDouble();
			}
			this.indexVector(path, vector);
			counter++;
		}
		in.close();
		int numDistractorVectors = counter - numDatabaseVectors;
		System.out.println("\n" + numDistractorVectors + " vectors indexed from the distractors file!");

		// add random vectors
		System.out.println("Indexing random vectors..");
		Random rand = new Random(1);
		while (counter < maxNumVectors) {
			if (counter % 100 == 0) {
				System.out.print("+ ");
			}
			double[] vladVector = new double[vectorLength];
			for (int i = 0; i < vectorLength; i++) {
				vladVector[i] = rand.nextGaussian();
			}
			this.indexVector("random", vladVector);
			counter++;
		}
		int numRandomVectors = counter - (numDatabaseVectors + numDistractorVectors);
		System.out.println("\n" + numRandomVectors + " additional random vectors were indexed!");
		System.out.println(counter + " vectors indexed in total!");
	}

}
