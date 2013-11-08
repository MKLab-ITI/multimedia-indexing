package gr.iti.mklab.visual.utilities;

import java.util.Comparator;

/**
 * This class implements the Comparator interface and is used for feeding distance calculation results in a
 * BoundedPriorityQueue during k-nearest neighbor search. Each Result has an internalId, an externalId and a
 * distance.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class Result implements Comparator<Result> {

	/**
	 * The distance.
	 */
	private double distance;

	/**
	 * The internal id of the returned result.
	 */
	private int iid;

	/**
	 * The external id of the returned result.
	 */
	private String id;

	public Result() {
	}

	public Result(int internalId, double distance) {
		this.iid = internalId;
		this.distance = distance;
	}

	public Result(String id, double distance) {
		this.id = id;
		this.distance = distance;
	}

	public Result(int internalId, String id, double distance) {
		this.iid = internalId;
		this.id = id;
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

	public double getDistance() {
		return distance;
	}

	public int getInternalId() {
		return iid;
	}

	public void setInternalId(int internalId) {
		this.iid = internalId;
	}

	public String getId() {
		return id;
	}

	public void setId(String externalId) {
		this.id = externalId;
	}

}
