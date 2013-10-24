package gr.iti.mklab.download;

import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

/**
 * This class represents an image download task. It implements the Callable interface and can be used for
 * multi-threaded image download.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownload implements Callable<ImageDownloadResult> {

	/**
	 * The URL where the image should be downloaded from.
	 */
	private String imageUrl;

	/**
	 * The image identifier.
	 */
	private String imageId;

	/**
	 * The directory where the image will be downloaded (temporarily or permanently).
	 */
	private String downloadFolder;

	/**
	 * Whether to store a thumbnail of the downloaded image.
	 */
	private boolean saveThumb;

	/**
	 * Whether to store the original image.
	 */
	private boolean saveOriginal;

	/**
	 * Whether to follow redirects (note that when redirects are followed, a wrong image may be downloaded).
	 */
	private boolean followRedirects;

	/**
	 * The value to use in HttpURLConnection.setConnectTimeout()
	 */
	public static final int connectionTimeout = 5000;

	/**
	 * The value to use in HttpURLConnection.setReadTimeout()
	 */
	public static final int readTimeout = 5000;

	/**
	 * The number of connection retries.
	 */
	public static final int maxRetries = 0; // currently not used

	/**
	 * The size of the thumbnail in pixels.
	 */
	public static final int thumbnailSizeInPixels = 200 * 200;

	/**
	 * If set to true, debug output is displayed.
	 */
	public boolean debug = false;

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * Constructor with 3 arguments.
	 * 
	 * @param urlStr
	 *            The url where the image is downloaded from
	 * @param id
	 *            The image identifier
	 * @param downloadFolder
	 *            The folder where the image is temporarily downloaded
	 */
	public ImageDownload(String urlStr, String id, String downloadFolder) {
		this.imageUrl = urlStr;
		this.imageId = id;
		this.downloadFolder = downloadFolder;
		this.saveThumb = false;
		this.saveOriginal = false;
		this.followRedirects = false;
	}

	/**
	 * Constructor with 6 arguments.
	 * 
	 * @param urlStr
	 *            The url where the image is downloaded from
	 * @param id
	 *            The image identifier (used to name the image file after download)
	 * @param downloadFolder
	 *            The folder where the image is downloaded
	 * @param saveThumb
	 *            Whether a thumbnail of the image should be saved
	 * @param saveOriginal
	 *            Whether the original image should be saved
	 * @param followRedirects
	 *            Whether redirects should be followed
	 */
	public ImageDownload(String urlStr, String id, String downloadFolder, boolean saveThumb,
			boolean saveOriginal, boolean followRedirects) {
		this.imageUrl = urlStr;
		this.imageId = id;
		this.downloadFolder = downloadFolder;
		this.saveThumb = saveThumb;
		this.saveOriginal = saveOriginal;
		this.followRedirects = followRedirects;
	}

	@Override
	/**
	 * Returns an ImageDownloadResult object from where the BufferedImage object and the image identifier can be
	 * obtained.
	 */
	public ImageDownloadResult call() throws Exception {
		if (debug)
			System.out.println("Downloading image " + imageUrl + " started.");
		BufferedImage image = downloadImage();
		if (debug)
			System.out.println("Downloading image " + imageUrl + " completed.");
		return new ImageDownloadResult(imageId, imageUrl, image);
	}

	/**
	 * Download of an image by URL.
	 * 
	 * @return The image as a BufferedImage object.
	 * @throws Exception
	 */
	private BufferedImage downloadImage() throws Exception {
		BufferedImage image = null;
		// try to recognize the type of the image from the url so that the correct format and file extension
		// are used when saving the thumbnail image or the original image.
		// In case that the url does not contain a known image extension, the jpg extension is used.
		String[] splitted = imageUrl.split("\\.");
		String fileExtension = (splitted[splitted.length - 1]).toLowerCase();
		if (!fileExtension.equals("jpg") && !fileExtension.equals("jpeg") && !fileExtension.equals("png")
				&& !fileExtension.equals("bmp") && !fileExtension.equals("gif")) {
			fileExtension = "jpg";
		}
		// this name filename will be used for the saved image file
		String imageFilename = downloadFolder + imageId + "." + fileExtension;

		// initialize the url, checking that it is valid
		URL url = null;
		try {
			url = new URL(imageUrl);
		} catch (MalformedURLException e) {
			System.out.println("Malformed url exception. Url: " + imageUrl);
			throw e;
		}

		HttpURLConnection conn = null;
		FileOutputStream fos = null;
		ReadableByteChannel rbc = null;
		InputStream in = null;
		boolean success = false;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(followRedirects);
			conn.setConnectTimeout(connectionTimeout); // TO DO: add retries when connections times out
			conn.setReadTimeout(readTimeout);
			conn.connect();
			success = true;
		} catch (Exception e) {
			System.out.println("Connection related exception at url: " + imageUrl);
			throw e;
		} finally {
			if (!success) {
				conn.disconnect();
			}
		}

		success = false;
		try {
			in = conn.getInputStream();
			success = true;
		} catch (Exception e) {
			System.out.println("Exception when getting the input stream from the connection at url: "
					+ imageUrl);
			throw e;
		} finally {
			if (!success) {
				in.close();
			}
		}

		rbc = Channels.newChannel(in);

		// if an Exception is thrown in the following code, the file that was created must be deleted
		try {
			// just copy the file
			fos = new FileOutputStream(imageFilename);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
			in.close();
			rbc.close();
			conn.disconnect();
			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFilename));
			} catch (IllegalArgumentException e) {
				// this exception is probably thrown because of a greyscale jpeg image
				System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename + " URL: "
						+ imageUrl);
				image = ImageIOGreyScale.read(new File(imageFilename)); // retry with the modified class
			}
		} catch (Exception e) { // in case of any other exception delete the image and re-throw the exception
			System.out.println("Exception: " + e.toString() + " | Image: " + imageFilename + " URL: "
					+ imageUrl);
			throw (e);
		} finally {
			if (image == null) { // if the image could not be read into a BufferedImage object then delete it
				File imageFile = new File(imageFilename);
				imageFile.delete();
				System.out.println("Deleting image with id " + imageId + ", url: " + imageUrl);
				throw new Exception("Could not read into BufferedImage");
			} else {
				if (saveThumb) { // save a thumbnail of the original image
					ImageScaling scale = new ImageScaling(thumbnailSizeInPixels);
					BufferedImage scaledImage = scale.maxPixelsScaling(image);
					FileOutputStream out = new FileOutputStream(new File(imageFilename.replace("."
							+ fileExtension, "-thumb." + fileExtension)));
					ImageIO.write(scaledImage, fileExtension, out);
					out.close();
				}
				if (!saveOriginal) {// delete the original image if needed
					File imageFile = new File(imageFilename);
					imageFile.delete();
				}
			}
			// close the open streams
			fos.close();
			in.close();
			rbc.close();
			conn.disconnect();
		}
		return image;
	}

	/**
	 * Example of a single image download using this class.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		String urlStr = "http://upload.wikimedia.org/wikipedia/commons/5/58/Sunset_2007-1.jpg";
		String id = "Sunset_2007-1";
		String downloadFolder = "images/";
		boolean saveThumb = true;
		boolean saveOriginal = true;
		boolean followRedirects = false;
		ImageDownload imdown = new ImageDownload(urlStr, id, downloadFolder, saveThumb, saveOriginal,
				followRedirects);
		imdown.setDebug(true);

		ImageDownloadResult imdr = imdown.call();
		System.out.println("Getting the BufferedImage object of the downloaded image.");
		imdr.getImage();
		System.out.println("Reading the downloaded image thumbnail into a BufferedImage object.");
		ImageIO.read(new File(downloadFolder + id + "-thumb.jpg"));
		System.out.println("Success!");

	}
}
