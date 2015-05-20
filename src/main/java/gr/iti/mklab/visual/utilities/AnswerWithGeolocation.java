package gr.iti.mklab.visual.utilities;

import com.javadocmd.simplelatlng.LatLng;

/**
 * This class extends {@link Answer} with geolocation data for the returned results.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 */
public class AnswerWithGeolocation extends Answer {

	/**
	 * The geolocations of the returned results.
	 */
	LatLng[] geolocations;

	/**
	 * Time taken for geolocation look-up (ms).
	 */
	private long geolocationLookupTime;

	public AnswerWithGeolocation(String[] ids, double[] distances, LatLng[] geolocations,
			long nameLookupTime, long indexSearchTime, long geolocationLookupTime) {
		super(ids, distances, nameLookupTime, indexSearchTime);
		this.geolocations = geolocations;
		this.geolocationLookupTime = geolocationLookupTime;
	}

}
