package gr.iti.mklab.visual.mapreduce;

import gr.iti.mklab.download.ImageDownloadResult;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * This class represents a hadoop image download task. It implements the Callable interface and can be used for
 * multi-threaded image download.
 *
 * @author Katerina Andreadou
 */
public class HadoopImageDownload implements Callable<ImageDownloadResult> {

    /**
     * The URL where the image should be downloaded from.
     */
    private String imageUrl;

    /**
     * The image identifier.
     */
    private String imageId;

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
     * If set to true, debug output is displayed.
     */
    public boolean debug = false;

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Constructor with 6 arguments.
     *
     * @param urlStr          The url where the image is downloaded from
     * @param id              The image identifier (used to name the image file after download)
     * @param followRedirects Whether redirects should be followed
     */
    public HadoopImageDownload(String urlStr, String id, boolean followRedirects) {
        this.imageUrl = urlStr;
        this.imageId = id;
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
        InputStream in = null;
        try { // first try reading with the default class
            URL url = new URL(imageUrl);

            HttpURLConnection conn = null;

            boolean success = false;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(followRedirects);
                conn.setConnectTimeout(connectionTimeout); // TO DO: add retries when connections times out
                conn.setReadTimeout(readTimeout);
                conn.connect();
                success = true;
            } catch (Exception e) {
                //System.out.println("Connection related exception at url: " + imageUrl);
                //throw e;
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
                /*System.out.println("Exception when getting the input stream from the connection at url: "
                        + imageUrl);
                throw e;*/
            } finally {
                if (!success) {
                    in.close();
                }
            }
            image = ImageIO.read(in);
        } catch (IllegalArgumentException e) {
            // this exception is probably thrown because of a greyscale jpeg image
            System.out.println("Exception: " + e.getMessage() + " | Image: " + imageUrl);
            image = ImageIOGreyScale.read(in); // retry with the modified class
        } catch (MalformedURLException e) {
            System.out.println("Malformed url exception. Url: " + imageUrl);
            //throw e;
        }
        return image;
    }
}

