multimedia-indexing
===================

A framework for large-scale feature extraction, indexing and retrieval. 


# Getting Started

This page contains instructions on how to use the library for:

-   Extracting SURF or SIFT features from an image

-   Aggregating a set of local descriptors into a VLAD vector

-   Applying dimensionality reduction using PCA

-   Creating and querying a linear index

-   Applying Product Quantization

## Extracting SURF or SIFT features from an image

This can be done using the classes in the `gr.iti.mklab.visual.extraction` package. `SURFExtractor `and `SIFTExtractor `implement SURF and SIFT extraction respectively and are actually wrappers of the corresponding classes in the [BoofCV][] library that only expose the most important parameters of the extraction process. Here is a simple example on how to extract SURF features from an image:

1.  Initialize the extractor: 

    `AbstractFeatureExtractor surf = new SURFExtractor();`

2.  Read the image file into a BufferedImage object: 

    `BufferedImage image = ImageIO.read(new File("sample.jpg"));`

3.  Obtain the features in a two-dimensional double array:

    `double[][] features = surf.extractFeatures(image);`

SIFT features can be obtained in a similar fashion. You can also have a look at the 

`SURForSIFTExtractionExample` class that performs SURF or SIFT extraction from a set of images contained in a folder and writes the extracted features in binary or text files. In this class you can also set the maximum size in pixels at which images are scaled prior to feature extraction.

## Aggregating a set of features into a VLAD vector

This can be done using the `VladAggregator `class of the `gr.iti.mklab.visual.aggregation` package. The constructor of this class takes as a parameter a two-dimensional double array that represents a learned codebook (visual vocabulary). Here is an example on how to compute a VLAD vector from a set of features :

1.  Read a codebook of 128 centroids that was learned using 64 dimensional SURF features and is written in a csv file (one centroid per line):

    `double [][] codebook = AbstractFeatureAggregator.readQuantizer("C:/codebook.csv", 128, 64);`

2.  Initialize the aggregator:

    `AbstractFeatureAggregator vlad = new VladAggregator(codebook);`

3.  Obtain the VLAD vector computed from a set of SURF features (represented as a two-dimensional double array) in a double array:

    `double [] vladVector = vlad.aggregate(features);`

In [this page][] we provide pre-computed SURF and SIFT codebooks. However, you can also create your own codebooks using the classes of the  `gr.iti.mklab.visual.quantization `package and specifically the `CodebookLearning `class. This class uses a slightly modified (to provide additional output) version of [Weka][]'s `SimpleKMeans` class that implements **multi-threaded** k-means clustering. You might also find handy the `SampleLocalFeatures` class that can be used for taking random samples from a large set of local features.  

## Applying dimensionality reduction using PCA

Comming soon!

## Creating and querying a linear index

Comming soon!

## Applying Product Quantization

Comming soon!

  [BoofCV]: http://boofcv.org
  [this page]: http://http://www.socialsensor.eu/results/software/79-image-search-testbed
  [Weka]: http://www.cs.waikato.ac.nz/ml/weka/


We try to follow the semantic versioning guidelines specified here: http://www.semver.org/
