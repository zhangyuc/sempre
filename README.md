Running the code
----------------

1. Download the dependencies and the dataset:

        ./pull-dependencies core
        ./pull-dependencies corenlp
        ./pull-dependencies tables
        ./pull-dependencies tables-data

  The dataset lives in `lib/data/tables/`

2. Compile the source:

        ant tables

  This will produce JAR files in the `libsempre` directory as usual.

3. The following command train and test on the first train-dev split:

        ./run @mode=tables @data=u-1 @feat=all @train=1 @grammar=extended @macro=1

  * To use the base grammar only, set @macro=0
  * To train and test on the other two train-dev splits, use @data=u-2 or @data=u-3
  * To train and test on the train-test split, use @data=test

Other hyperparameters
------------

  * To change beamsize, attach "-beamSize (value)" to the command line
  * To change K for the K-nearest neighbor, attach "-K (value)" to the command line
  * To control the base grammar usage, attach "-maxExploration (value)" to the command line 
