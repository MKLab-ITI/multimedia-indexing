package gr.iti.mklab.visual.utilities;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

/**
 * Objects of this class are used to wrap an regular object that contains metadata for a vector into an object
 * that can be stored as a BDB Entity.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
@Entity
public class MetaDataEntity {

	@PrimaryKey
	private int id;

	/**
	 * The class of a metadata object should be annotated as @Persistent!
	 */
	private Object metaData;

	public MetaDataEntity(int id, Object metaData) {

		this.id = id;
		this.metaData = metaData;
	}

	public Object getMetaData() {
		return metaData;
	}

}
