package gr.iti.mklab.download;

import gr.iti.mklab.visual.mapreduce.HadoopImageDownload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class implements multi-threaded image downloading.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownloader {

	private ExecutorService downloadExecutor;

	private CompletionService<ImageDownloadResult> pool;

	/** The current number of tasks whose termination is pending. **/
	private int numPendingTasks;

	/**
	 * The maximum allowable number of pending tasks, used to limit the memory usage.
	 */
	private final int maxNumPendingTasks;

	/**
	 * The folder where the original image and/or its thumbnail should be saved.
	 **/
	private String downloadFolder;

	/**
	 * Whether the original image should be saved.
	 */
	private boolean saveOriginal;

	/**
	 * Whether a thumb of the original image should be saved.
	 */
	private boolean saveThumb;

	/**
	 * Whether redirects should be followed.
	 */
	private boolean followRedirects;

	/**
	 * Constructor of the multi-threaded download class.
	 * 
	 * @param numThreads
	 *            the number of download threads to use
	 * @param downloadFolder
	 *            the download folder
	 */
	public ImageDownloader(String downloadFolder, int numThreads) {
		this.downloadFolder = downloadFolder;
		saveOriginal = false;
		saveThumb = true;
		followRedirects = false;

		downloadExecutor = Executors.newFixedThreadPool(numThreads);
		pool = new ExecutorCompletionService<ImageDownloadResult>(downloadExecutor);
		numPendingTasks = 0;
		maxNumPendingTasks = numThreads * 10;

	}

	/**
	 * Submits a new image download task.
	 * 
	 * @param URL
	 *            The url of the image
	 * @param id
	 *            The id of the image (used to name the image file after download)
	 */
	public void submitImageDownloadTask(String URL, String id) {
		Callable<ImageDownloadResult> call = new ImageDownload(URL, id, downloadFolder, saveThumb,
				saveOriginal, followRedirects);
		pool.submit(call);
		numPendingTasks++;
	}

    /**
     * Submits a new hadoop image download task.
     *
     * @param URL
     *            The url of the image
     * @param id
     *            The id of the image (used to name the image file after download)
     */
    public void submitHadoopDownloadTask(String URL, String id) {
        Callable<ImageDownloadResult> call = new HadoopImageDownload(URL, id, followRedirects);
        pool.submit(call);
        numPendingTasks++;
    }

	/**
	 * Gets an image download results from the pool.
	 * 
	 * @return the download result, or null in no results are ready
	 * @throws Exception
	 *             for a failed download task
	 */
	public ImageDownloadResult getImageDownloadResult() throws Exception {
		Future<ImageDownloadResult> future = pool.poll();
		if (future == null) { // no completed tasks in the pool
			return null;
		} else {
			try {
				ImageDownloadResult imdr = future.get();
				return imdr;
			} catch (Exception e) {
				throw e;
			} finally {
				// in any case (Exception or not) the numPendingTask should be reduced
				numPendingTasks--;
			}
		}
	}

	/**
	 * Gets an image download result from the pool, waiting if necessary.
	 * 
	 * @return the download result
	 * @throws Exception
	 *             for a failed download task
	 */
	public ImageDownloadResult getImageDownloadResultWait() throws Exception {
		try {
			ImageDownloadResult imdr = pool.take().get();
			return imdr;
		} catch (Exception e) {
			throw e;
		} finally {
			// in any case (Exception or not) the numPendingTask should be reduced
			numPendingTasks--;
		}
	}

	/**
	 * Returns true if the number of pending tasks is smaller than the maximum allowable number.
	 * 
	 * @return
	 */
	public boolean canAcceptMoreTasks() {
		if (numPendingTasks < maxNumPendingTasks) {
			return true;
		} else {
			return false;
		}
	}

	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	public void setSaveOriginal(boolean saveOriginal) {
		this.saveOriginal = saveOriginal;
	}

	public void setSaveThumb(boolean saveThumb) {
		this.saveThumb = saveThumb;
	}

	/**
	 * Shuts the download executor down, waiting for up to 60 seconds for the remaining tasks to complete. See
	 * http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
	 * 
	 */
	public void shutDown() {
		downloadExecutor.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!downloadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
				downloadExecutor.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!downloadExecutor.awaitTermination(60, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			downloadExecutor.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * This method exemplifies multi-threaded image download from a list of urls. It uses 5 download threads.
	 * 
	 * @param dowloadFolder
	 *            Full path to the folder where the images are downloaded
	 * @param urlsFile
	 *            Full path to the file that contains the ids and urls (space separated) of the images (one
	 *            per line)
	 * @param numUrls
	 *            The total number of urls to consider
	 * @param urlsToSkip
	 *            How many urls (from the top of the file to be skipped)
	 * @throws Exception
	 */
	public static void downloadFromUrlsFile(String dowloadFolder, String urlsFile, int numUrls, int urlsToSkip)
			throws Exception {
		long start = System.currentTimeMillis();
		int numThreads = 10;
		BufferedReader in = new BufferedReader(new FileReader(new File(urlsFile)));
		for (int i = 0; i < urlsToSkip; i++) {
			in.readLine();
		}
		ImageDownloader downloader = new ImageDownloader(dowloadFolder, numThreads);
		int submittedCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;
		String line = "";
		while (true) {
			String url;
			String id = "";
			// if there are more task to submit and the downloader can accept more tasks then submit
			while (submittedCounter < numUrls && downloader.canAcceptMoreTasks()) {
				line = in.readLine();
				url = line.split("\\s+")[1];
				id = line.split("\\s+")[0];
				downloader.submitImageDownloadTask(url, id);
				submittedCounter++;
			}
			// if are submitted taks that are pending completion ,try to consume
			if (completedCounter + failedCounter < submittedCounter) {
				try {
					downloader.getImageDownloadResultWait();
					completedCounter++;
					System.out.println(completedCounter + " downloads completed!");
				} catch (Exception e) {
					failedCounter++;
					System.out.println(failedCounter + " downloads failed!");
					System.out.println(e.getMessage());
				}
			}
			// if all tasks have been consumed then break;
			if (completedCounter + failedCounter == numUrls) {
				downloader.shutDown();
				in.close();
				break;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start) + " ms");
		System.out.println("Downloaded images: " + completedCounter);
		System.out.println("Failed images: " + failedCounter);
	}

	/**
	 * Calls the downloadFromUrlsFile.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String dowloadFolder = "images/";
		String urlsFile = "urls.txt";
		int numUrls = 1000;
		int urlsToSkip = 0;
		downloadFromUrlsFile(dowloadFolder, urlsFile, numUrls, urlsToSkip);
	}
}
