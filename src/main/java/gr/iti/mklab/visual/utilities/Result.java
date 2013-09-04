package gr.iti.mklab.visual.utilities;

import java.util.Comparator;

import com.javadocmd.simplelatlng.LatLng;

/**
 * This is a helper class. Each Result has an id, a name and a distance (from a query) vector. Objects of this
 * class are passed into a BoundedPriorityQueue during k-nearest neighbor calculation.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Result implements Comparator<Result> {

	/**
	 * The internal id of the returned result.
	 */
	private int internalId;

	/**
	 * The external id of the returned result.
	 */
	private String externalId;

	public LatLng getGeolocation() {
		return geolocation;
	}

	public void setGeolocation(LatLng geolocation) {
		this.geolocation = geolocation;
	}

	private LatLng geolocation;

	/**
	 * The distance from the query.
	 */
	private double distance;

	public int getInternalId() {
		return internalId;
	}

	public void setInternalId(int internalId) {
		this.internalId = internalId;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public double getDistance() {
		return distance;
	}

	public Result() {
	}

	public Result(int internalId, double distance) {
		this.internalId = internalId;
		this.distance = distance;
	}

	public int compare(Result o1, Result o2) {
		if ((o1).getDistance() > (o2).getDistance())
			return -1;
		else if ((o1).getDistance() < (o2).getDistance())
			return 1;
		else
			return 0;
	}

	public String toString() {
		String output = "ID: " + externalId + " internalID: " + internalId + " distance: " + distance;
		return output;
	}

}
