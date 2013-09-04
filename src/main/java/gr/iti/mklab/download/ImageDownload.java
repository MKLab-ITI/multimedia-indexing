package gr.iti.mklab.download;

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

import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

/**
 * Class for downloading images.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownload implements Callable<ImageDownload> {

	/**
	 * The URL where the image should be downloaded from.
	 */
	private String urlStr;

	private String id;

	private String downloadFolder = null;

	private BufferedImage image;

	private BufferedImage scaledImage;

	public static final int connectionTimeout = 5000;
	public static final int maxRetries = 2;

	public ImageDownload(String urlStr, String id, String downloadFolder) {
		this.urlStr = urlStr;
		this.id = id;
		this.downloadFolder = downloadFolder;
	}

	@Override
	public ImageDownload call() throws Exception {
		// System.out.println("Downloading image " + urlStr);
		downloadImage();
		// System.out.println("Downloading image " + urlStr + " took " + (end - start) + " ms");
		return this;
	}

	/**
	 * Download of an image by URL.
	 * 
	 * @param urlStr
	 *            The URL of a Photo
	 * @throws Exception
	 */
	private void downloadImage() throws Exception {
		// try to recognize the type of the image from the url.
		// in case that the url does not contain a known image extension a jpg extension.
		String[] splitted = urlStr.split("\\.");
		String fileExtension = (splitted[splitted.length - 1]).toLowerCase();
		if (!fileExtension.equals("jpg") && !fileExtension.equals("jpeg") && !fileExtension.equals("png") && !fileExtension.equals("bmp") && !fileExtension.equals("gif")) {
			fileExtension = "jpg";
		}
		// this name filename will be used for the saved image file
		String imageFilename = downloadFolder + id + "." + fileExtension;

		// initiallize the url, checking that it is valid
		URL url = null;
		try {
			url = new URL(urlStr);
		} catch (MalformedURLException e) {
			System.out.println("Malformed url exception. Url: " + urlStr);
			throw e;
		}

		HttpURLConnection conn = null;
		FileOutputStream fos = null;
		ReadableByteChannel rbc = null;
		InputStream in = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			// when redirects are followed, a wrong image may be downloaded, e.g. in Flickr
			// HttpURLConnection.setFollowRedirects(true);
			// System.out.println(conn.getConnectTimeout());
			conn.setInstanceFollowRedirects(false);
			conn.setConnectTimeout(connectionTimeout); // TO DO: add retries when connections times out
			conn.setReadTimeout(connectionTimeout);
			conn.connect(); // open connection
			in = conn.getInputStream();
			rbc = Channels.newChannel(in);
		} catch (Exception e) {
			System.out.println("Connection related exception at url: " + urlStr + " closing connection and streams..");
			rbc.close();
			in.close();
			conn.disconnect(); // close the connection
			throw e;
		}

		// if an Exception is thrown in the following code, the file that was created must be deleted.
		try {
			// first just copy the file
			fos = new FileOutputStream(imageFilename);
			fos.getChannel().transferFrom(rbc, 0, 1 << 24);
			fos.close();
			rbc.close();
			in.close();
			conn.disconnect(); // close the connection

			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFilename));
			} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
				// This exception is probably because of a grayscale jpeg image.
				System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename + " URL: " + urlStr);
				image = ImageIOGreyScale.read(new File(imageFilename));
			}
		} catch (Exception e) { // in case of any other exception delete the image and re-throw the exception
			System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename + " URL: " + urlStr);
			throw (e);
		} finally {
			if (image == null) {
				File imageFile = new File(imageFilename);
				imageFile.delete();
				System.out.println("Deleting image with " + id + " url: " + urlStr);
				throw new Exception("Could not read into BufferedImage");
			}

			if (image != null) {
				// save a scaled instance of the original file
				ImageScaling scale = new ImageScaling(200 * 200);
				scaledImage = scale.maxPixelsScaling(image);
				FileOutputStream out = new FileOutputStream(new File(imageFilename));
				ImageIO.write(scaledImage, fileExtension, out);
				out.close();
			}

			fos.close();
			in.close();
			rbc.close();
			conn.disconnect(); // close the connection
		}

	}

	public String getUrlStr() {
		return urlStr;
	}

	public BufferedImage getIm() {
		return image;
	}

	public String getId() {
		return id;
	}
}
