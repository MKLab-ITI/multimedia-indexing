package gr.iti.mklab.visual.vectorization;

/**
 * This class represents the result of an image vectorization task.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVectorizationResult {

	/**
	 * The name of the image.
	 */
	private String imageName;
	/**
	 * The image vector.
	 */
	private double[] imageVector;
	/**
	 * Any exception thrown during vectorization.
	 */
	private String exceptionMessage;

	public ImageVectorizationResult(String imageName, double[] imageVector, String exceptionMessage) {
		this.imageName = imageName;
		this.imageVector = imageVector;
		this.exceptionMessage = exceptionMessage;
	}

	public String getExceptionMessage() {
		return exceptionMessage;
	}

	public String getImageName() {
		return imageName;
	}

	public double[] getImageVector() {
		return imageVector;
	}

}
