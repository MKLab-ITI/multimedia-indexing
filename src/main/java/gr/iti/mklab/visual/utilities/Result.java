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
	 * The external id of the returned result.
	 */
	private String externalId;

	/**
	 * The id of the returned result.
	 */
	private int id;

	public Result() {
	}

	public Result(int id, double distance) {
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

	public String getExternalId() {
		return externalId;
	}

	public int getId() {
		return id;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public void setId(int internalId) {
		this.id = internalId;
	}

	public String toString() {
		String output = "id: " + id + "  distance: " + distance;
		return output;
	}

}
