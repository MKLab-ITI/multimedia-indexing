package gr.iti.mklab.visual.datastructures;

import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.AnswerWithGeolocation;
import gr.iti.mklab.visual.utilities.MetaDataEntity;
import gr.iti.mklab.visual.utilities.Result;

import java.io.File;
import java.util.Date;
import java.util.List;

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
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentNotFoundException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;

/**
 * This class abstracts operations related to persistence and id lookup from the actual indexing structures.
 * The term id is used for the name or other identifier of the vectors being indexed while the term iid
 * (internal id) is used for the id assigned to a vector internally be each indexing structure. <br>
 * An id is of type String and is kept in disk while iid is of type int and is loaded in memory.<br>
 * Berkeley DB (BDB) is used for efficient persistent storage.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public abstract class AbstractSearchStructure {

	/**
	 * The total memory to be used by the BDB, 100Mb by default. Larger values will allow faster id lookup.
	 */
	protected final long cacheSize = 1024 * 1024 * 100;

	/**
	 * Whether the environment will be transactional. If true, ensures that the dbs will not be corrupted. <br>
	 * For more information on what this means, refer to the BDB documentation.
	 */
	protected final boolean transactional = true;

	/**
	 * The length of the raw vectors being indexed.
	 */
	protected int vectorLength;

	/**
	 * Keeps track of the total number of indexed vectors, acts as an auto-increment primary key field.
	 */
	protected int loadCounter;

	/**
	 * Whether the index will be loaded in memory. We can avoid loading the index in memory when we only want
	 * to perform indexing.
	 */
	protected boolean loadIndexInMemory;

	/**
	 * The maximum number of vectors that can be indexed.
	 */
	protected final int maxNumVectors;

	/**
	 * Whether to initialize the load counter by counting the size of the {@link #iidToIdDB}. This operation
	 * incurs a large cost when loading very large indices. It can be set to false for efficiency reasons. In
	 * that case, the load counter should be set manually.
	 */
	private boolean countSizeOnLoad;

	/**
	 * Whether the index should open only for read access. This allows multiple opens of the index.
	 */
	protected boolean readOnly;

	/**
	 * The database environment. Access to this field is needed by specific indexing structures that implement
	 * persistence.
	 */
	protected Environment dbEnv;

	/**
	 * BDB store holding id to iid mappings, required during indexing to fast check if a name is already
	 * indexed.
	 */
	protected Database idToIidDB;

	/**
	 * BDB store holding iid to id mappings, required for name look-up during nn search.
	 */
	protected Database iidToIdDB;

	/**
	 * BDB store holding iid to longitude-latitude mappings, required for geolocation look-up during nn
	 * search.
	 */
	protected Database iidToGeolocationDB;

	/**
	 * BDB store holding iid to metadata mappings, required for metadata look-up during nn search.
	 */
	protected EntityStore iidToMetadataDB;

	/**
	 * Average time taken for internal vector indexing operations.
	 */
	private long totalInternalVectorIndexingTime;

	/**
	 * Average time taken to create an id to idd and the reverse mapping.
	 */
	private long totalIdMappingTime;

	/**
	 * Average total time taken to index a vector.
	 */
	private long totalVectorIndexingTime;

	/**
	 * Whether to create/load geolocation db.
	 */
	protected final boolean useGeolocation = false;

	/**
	 * Whether to create/load metadata db.
	 */
	protected final boolean useMetaData = false;

	/**
	 * Constructor. Used when we count the size of the database when opening it.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 */
	protected AbstractSearchStructure(int vectorLength, int maxNumVectors, boolean readOnly) {
		this(vectorLength, maxNumVectors, readOnly, true, 0, true);
	}

	/**
	 * Constructor. Used when we want to avoid counting the database size and to use a preset value for the
	 * load counter.
	 * 
	 * @param vectorLength
	 *            The dimensionality of the VLAD vectors being indexed
	 * @param maxNumVectors
	 *            The maximum allowable size (number of vectors) of the index
	 * @param readOnly
	 *            If true the persistent store will opened only for read access (allows multiple opens)
	 * @param countSizeOnLoad
	 *            Whether the load counter will be initialized by the size of the persistent store
	 * @param loadCounter
	 *            The initial value of the load counter
	 * @param loadIndexInMemory
	 *            Whether to load the index in memory, we can avoid loading the index in memory when we only
	 *            want to perform indexing
	 */
	protected AbstractSearchStructure(int vectorLength, int maxNumVectors, boolean readOnly,
			boolean countSizeOnLoad, int loadCounter, boolean loadIndexInMemory) {
		this.vectorLength = vectorLength;
		this.loadCounter = loadCounter;
		this.maxNumVectors = maxNumVectors;
		this.readOnly = readOnly;
		this.countSizeOnLoad = countSizeOnLoad;
		this.loadIndexInMemory = loadIndexInMemory;
	}

	/**
	 * Updates the index with the given vector. This is a synchronized method, i.e. when a thread calls this
	 * method, all other threads wait for the first thread to complete before executing the method. This
	 * ensures that the persistent BDB store will remain consistent when multiple threads call the indexVector
	 * method.
	 * 
	 * @param id
	 *            The id of the vector
	 * @param vector
	 *            The vector
	 * @return True if the vector is successfully indexed, false otherwise.
	 * @throws Exception
	 */
	public synchronized boolean indexVector(String id, double[] vector) throws Exception {
		long startIndexing = System.currentTimeMillis();
		// check if we can index more vectors
		if (loadCounter >= maxNumVectors) {
			System.out.println("Maximum index capacity reached, no more vectors can be indexed!");
			return false;
		}
		// check if name is already indexed
		if (isIndexed(id)) {
			System.out.println("Vector '" + id + "' already indexed!");
			return false;
		}
		// do the indexing
		// persist id to name and the reverse mapping
		long startMapping = System.currentTimeMillis();
		createMapping(id);
		totalIdMappingTime += System.currentTimeMillis() - startMapping;
		// method specific indexing
		long startInternalIndexing = System.currentTimeMillis();
		indexVectorInternal(vector);
		totalInternalVectorIndexingTime += System.currentTimeMillis() - startInternalIndexing;

		loadCounter++; // increase the loadCounter
		if (loadCounter % 100 == 0) { // debug message
			System.out.println(new Date() + " # indexed vectors: " + loadCounter);
		}
		totalVectorIndexingTime += System.currentTimeMillis() - startIndexing;
		return true;
	}

	/**
	 * This method should be implemented in all subclasses and do the operations required for indexing the
	 * given vector.
	 * 
	 * @param vector
	 *            The vector to be indexed
	 * @throws Exception
	 */
	protected abstract void indexVectorInternal(double[] vector) throws Exception;

	/**
	 * This method returns an {@link Answer} object, which contains the k nearest neighbors along with their
	 * ids and distances from the query vector, ordered by lowest distance. The methods calls
	 * {@link #computeNearestNeighborsInternal(int, double[])} and then performs name lookup.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return
	 * @param queryVector
	 *            The query vector
	 * @return The answer
	 * @throws Exception
	 */
	public Answer computeNearestNeighbors(int k, double[] queryVector) throws Exception {
		if (!loadIndexInMemory) {
			throw new Exception("Cannot execute query because the index is not loaded in memory!");
		}
		long start = System.nanoTime();
		BoundedPriorityQueue<Result> nnQueue = computeNearestNeighborsInternal(k, queryVector);
		long indexSearchTime = System.nanoTime() - start;

		Result[] nn = new Result[nnQueue.size()];
		nn = nnQueue.toArray(nn);

		start = System.nanoTime();
		for (int i = 0; i < nn.length; i++) { // attach external ids to the results
			int iid = nn[i].getInternalId();
			String id = getId(iid);
			nn[i].setExternalId(id);
		}
		long nameLookupTime = System.nanoTime() - start;

		if (!useMetaData) {
			return new Answer(nn, nameLookupTime, indexSearchTime);
		} else {
			start = System.nanoTime();
			LatLng[] geolocations = new LatLng[nn.length];
			for (int i = 0; i < nn.length; i++) { // attach external ids to the results
				int iid = nn[i].getInternalId();
				geolocations[i] = getGeolocation(iid);
			}
			long geolocationLookupTime = System.nanoTime() - start;
			return new AnswerWithGeolocation(nn, geolocations, nameLookupTime, indexSearchTime,
					geolocationLookupTime);
		}
	}

	/**
	 * This method returns a bounded priority queue of Result objects, which contains the k nearest neighbors
	 * along with their iids and distances from the query vector, ordered by lowest distance. Subclasses
	 * should implement this method.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return
	 * @param queryVector
	 *            The query vector
	 * @return A bounded priority queue of Result objects
	 * @throws Exception
	 */
	protected abstract BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k,
			double[] queryVector) throws Exception;

	/**
	 * This method returns an {@link Answer} object, which contains the k nearest neighbors along with their
	 * ids and distances from the query vector, ordered by lowest distance. The methods calls
	 * {@link #computeNearestNeighborsInternal(int, int)} and then performs id lookup.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return
	 * @param queryId
	 *            The id of the query vector
	 * @return The answer
	 * @throws Exception
	 */
	public Answer computeNearestNeighbors(int k, String queryId) throws Exception {
		int internalIdQuery = getInternalId(queryId);

		long start = System.nanoTime();
		BoundedPriorityQueue<Result> nnQueue = computeNearestNeighborsInternal(k, internalIdQuery);
		long indexSearchTime = System.nanoTime() - start;

		Result[] nn = new Result[nnQueue.size()];
		nn = nnQueue.toArray(nn);

		start = System.nanoTime();
		for (int i = 0; i < nn.length; i++) { // attach external ids to the results
			int iid = nn[i].getInternalId();
			String id = getId(iid);
			nn[i].setExternalId(id);
		}
		long nameLookupTime = System.nanoTime() - start;

		if (!useMetaData) {
			return new Answer(nn, nameLookupTime, indexSearchTime);
		} else {
			start = System.nanoTime();
			LatLng[] geolocations = new LatLng[nn.length];
			for (int i = 0; i < nn.length; i++) { // attach external ids to the results
				int iid = nn[i].getInternalId();
				geolocations[i] = getGeolocation(iid);
			}
			long geolocationLookupTime = System.nanoTime() - start;
			return new AnswerWithGeolocation(nn, geolocations, nameLookupTime, indexSearchTime,
					geolocationLookupTime);
		}
	}

	/**
	 * This method returns a bounded priority queue of Result objects, which contains the k nearest neighbors
	 * along with their iids and distances from the query vector, ordered by lowest distance. Subclasses
	 * should implement this method.
	 * 
	 * @param k
	 *            The number of nearest neighbors to return
	 * @param iid
	 *            The internal id of the query vector
	 * @return A bounded priority queue of Result objects
	 * @throws Exception
	 */
	protected abstract BoundedPriorityQueue<Result> computeNearestNeighborsInternal(int k, int iid)
			throws Exception;

	/**
	 * Returns the internal id assigned to the vector with the given id or -1 if the id is not found. Accesses
	 * the BDB store!
	 * 
	 * @param id
	 *            The id of the vector
	 * @return The internal id assigned to this vector or -1 if the id is not found.
	 */
	public int getInternalId(String id) {
		DatabaseEntry key = new DatabaseEntry();
		StringBinding.stringToEntry(id, key);
		DatabaseEntry data = new DatabaseEntry();
		// check if the id already exists in id to iid database
		if ((idToIidDB.get(null, key, data, null) == OperationStatus.SUCCESS)) {
			return IntegerBinding.entryToInt(data);
		} else {
			return -1;
		}
	}

	/**
	 * Returns the id of the vector which was assigned the given internal id or null if the internal id does
	 * not exist. Accesses the BDB store!
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @return The id mapped to the given internal id or null if the internal id does not exist
	 */
	public String getId(int iid) {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return null;
		}
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(iid, key);
		DatabaseEntry data = new DatabaseEntry();
		if ((iidToIdDB.get(null, key, data, null) == OperationStatus.SUCCESS)) {
			return StringBinding.entryToString(data);
		} else {
			System.out.println("Internal id " + iid + " is in range but id was not found..");
			System.out.println("Index is probably corrupted");
			System.exit(0);
			return null;
		}
	}

	/**
	 * Returns a {@link LatLng} object with the geolocation of the vector with the given internal id or null
	 * if the internal id does not exist. Accesses the BDB store!
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @return The geolocation mapped to the given internal id or null if the internal id does not exist
	 */
	public LatLng getGeolocation(int iid) {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return null;
		}
		DatabaseEntry key = new DatabaseEntry();
		IntegerBinding.intToEntry(iid, key);
		DatabaseEntry data = new DatabaseEntry();
		if ((iidToGeolocationDB.get(null, key, data, null) == OperationStatus.SUCCESS)) {
			TupleInput input = TupleBinding.entryToInput(data);
			double latitude = input.readDouble();
			double longitude = input.readDouble();
			LatLng geolocation = new LatLng(latitude, longitude);
			return geolocation;
		} else {
			System.out.println("Internal id " + iid + " is in range but gelocation was not found.");
			return null;
		}
	}

	/**
	 * Returns a {@link MetaDataEntity} object with the metadata of the vector with the given internal id or
	 * null if the internal id does not exist. Accesses the BDB store!
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @return The metadata mapped to the given internal id or null if the internal id does not exist
	 */
	public MetaDataEntity getMetadata(int iid) throws Exception {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return null;
		}
		PrimaryIndex<Integer, MetaDataEntity> primaryIndex = iidToMetadataDB.getPrimaryIndex(Integer.class,
				MetaDataEntity.class);
		return primaryIndex.get(null, iid, null);
	}

	/**
	 * This method is used to set the geolocation of a previously indexed vector. If the geolocation is
	 * already set, this method replaces it.
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @param latitude
	 * @param longitude
	 * @return true if geolocation is successfully set, false otherwise
	 */
	public boolean setGeolocation(int iid, double latitude, double longitude) {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return false;
		}
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry();

		IntegerBinding.intToEntry(iid, key);
		TupleOutput output = new TupleOutput();
		output.writeDouble(latitude);
		output.writeDouble(longitude);
		TupleBinding.outputToEntry(output, data);

		if (iidToGeolocationDB.put(null, key, data) == OperationStatus.SUCCESS) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to set the metadata of a previously indexed vector. If the metadata is already set,
	 * this methods replaces it.
	 * 
	 * @param iid
	 *            The internal id of the vector
	 * @param metaData
	 *            A java object of any class with the @persistent annotation
	 * @return true if metadata is successfully set, false otherwise
	 */
	public boolean setMetadata(int iid, Object metaData) {
		if (iid < 0 || iid > loadCounter) {
			System.out.println("Internal id " + iid + " is out of range!");
			return false;
		}
		MetaDataEntity mde = new MetaDataEntity(iid, metaData);
		PrimaryIndex<Integer, MetaDataEntity> primaryIndex = iidToMetadataDB.getPrimaryIndex(Integer.class,
				MetaDataEntity.class);

		if (primaryIndex.contains(iid)) {
			primaryIndex.put(null, mde);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * <b>{@link #getInternalId(String)} can always be called instead of this method at the same cost!</b> <br>
	 * Checks if the vector with the given id is already indexed. This method is useful to avoid re-indexing
	 * the same vector. Its convention is that if the given name is already in idToIidBDB, then the vector is
	 * indexed in all other structures e.g. iidToIdBDB. The rest of the checks are avoided for efficiency.
	 * Accesses the BDB store!
	 * 
	 * @param id
	 *            The id the vector
	 * @return true if the vector is indexed, false otherwise
	 */
	public boolean isIndexed(String id) {
		DatabaseEntry key = new DatabaseEntry();
		StringBinding.stringToEntry(id, key);
		DatabaseEntry data = new DatabaseEntry();
		if ((idToIidDB.get(null, key, data, null) == OperationStatus.SUCCESS)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to create a persistent mapping between the given id and an internal id (equal to
	 * the current value of {@link #loadCounter}). Should be called every time that a new vector is indexed.
	 * 
	 * @param id
	 *            The id
	 */
	private void createMapping(String id) {
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry();
		IntegerBinding.intToEntry(loadCounter, key);
		StringBinding.stringToEntry(id, data);
		iidToIdDB.put(null, key, data); // required during name look-up
		idToIidDB.put(null, data, key); // required during indexing
	}

	/**
	 * This method creates and/or opens the BDB databases with the appropriate parameters.
	 * 
	 * @throws Exception
	 */
	private void createOrOpenBDBDbs() throws Exception {
		// configuration for the mapping dbs
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.setAllowCreate(true); // db will be created if it does not exist
		dbConfig.setReadOnly(readOnly);
		dbConfig.setTransactional(transactional);
		// create/open mapping dbs using config
		iidToIdDB = dbEnv.openDatabase(null, "idToName", dbConfig);

		// if countSizeOnLoad is true, the id-name mappings are counted and the loadCounter is initialized
		if (countSizeOnLoad) {
			int idToNameMappings = (int) iidToIdDB.count();
			loadCounter = Math.min(idToNameMappings, maxNumVectors);
		}

		idToIidDB = dbEnv.openDatabase(null, "nameToId", dbConfig);

		if (useGeolocation) {// create/open geolocation db using config
			iidToGeolocationDB = dbEnv.openDatabase(null, "idToGeolocation", dbConfig);
		}

		if (useMetaData) {
			StoreConfig storeConfig = new StoreConfig(); // configuration of the entity store
			storeConfig.setAllowCreate(true); // store will be created if it does not exist
			storeConfig.setReadOnly(readOnly);
			storeConfig.setTransactional(transactional);
			iidToMetadataDB = new EntityStore(dbEnv, "idToMetadata", storeConfig);
			// int nameToMetadataMappings = (int) nameToMetadataBDB.getPrimaryIndex(String.class,
			// MediaFeedData.class).count(); // counting the size of an EntityStore
		}
	}

	/**
	 * This method creates and/or opens the BDB environment in the supplied directory. <br>
	 * TODO: The configuration can be tuned for being more efficient / less persistent!
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
		envConf.setAllowCreate(false); // initially we do not allow create
		envConf.setReadOnly(readOnly);
		envConf.setTransactional(transactional);
		envConf.setCacheSize(cacheSize);
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
	 * This method creates or opens (if it already exists) the BDB environment and dbs.
	 * 
	 * @param BDBEnvHome
	 *            The directory where the BDB environment will be created
	 * @throws Exception
	 */
	protected void createOrOpenBDBEnvAndDbs(String BDBEnvHome) throws Exception {
		createOrOpenBDBEnv(BDBEnvHome);
		createOrOpenBDBDbs();
	}

	/**
	 * Returns the current value of the loadCounter.
	 * 
	 * @return
	 */
	public int getLoadCounter() {
		return loadCounter;
	}

	/**
	 * This method can be called to output indexing time measurements.
	 */
	public void outputIndexingTimes() {
		System.out.println((double) totalInternalVectorIndexingTime / loadCounter
				+ " ms => internal indexing time");
		System.out.println((double) totalIdMappingTime / loadCounter + " ms => id mapping time");
		System.out.println((double) totalVectorIndexingTime / loadCounter + " ms => total indexing time");
		outputIndexingTimesInternal();
	}

	/**
	 * Should output index specific time measurements.
	 */
	protected abstract void outputIndexingTimesInternal();

	/**
	 * This method closes the open BDB environment and databases.
	 */
	public void close() {
		if (dbEnv != null) {
			// closing dbs
			iidToIdDB.close();
			idToIidDB.close();
			if (useGeolocation) {
				iidToGeolocationDB.close();
			}
			if (useMetaData) {
				iidToMetadataDB.close();
			}
			closeInternal();
			dbEnv.close(); // closing env
		} else {
			System.out.println("BDB environment is null!");
		}
	}

	/**
	 * Each subclass should implement this method to close the BDB databases that it uses.
	 */
	protected abstract void closeInternal();
}
