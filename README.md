# OCR SPARK APP RUN INFO


##### Cloudera branch

The project has been released to use spark 1.6. The branch name is spark-1.6.

These are the steps:

Run the next commands:

```
git clone -b spark-1.6 <git.repository>

cd spark_ocr_tesseract

mvn clean package
```

The following command will execute the job in the cluster:
```

 ----------------SPARK DINAMIC ALLOCATION---------- MOST STABLE 34 TASKS 18 CONTAINERS	35 CORES	140800 MEMORY ALLOCATED
 spark-submit  --master yarn-cluster --class "com.jene.cognitive.OcrTess4jSpark" --driver-memory 1g --driver-cores 1 --executor-memory 3g --executor-cores 2  --driver-java-options "-Denv=dev -Djna.library.path=$LD_LIBRARY_PATH:/usr/local/lib64 -Djava.library.path='$LD_LIBRARY_PATH:/usr/local/lib64 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" --conf spark.yarn.executor.memoryOverhead=5046 --conf spark.yarn.executor.memoryOverhead=5046 --conf spark.rdd.compress=true --conf spark.broadcast.compress=true --conf  "spark.executor.extraJavaOptions=-Djna.library.path=$LD_LIBRARY_PATH:/usr/local/lib64" --conf "spark.yarn.am.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf "spark.executor.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf "spark.driver.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf spark.yarn.maxAppAttempts=4  --conf spark.yarn.am.attemptFailuresValidityInterval=1h --conf spark.dynamicAllocation.enabled=true target/job-ocr-pdf-1.0.jar
 
 
  ----------------NO SPARK DINAMIC ALLOCATION---------- MOST STABLE 34 TASKS 23 CONTAINERS	45 CORES	181760 MEMORY ALLOCATED
 spark-submit  --master yarn-cluster --class "com.jene.cognitive.OcrTess4jSpark" --driver-memory 1g --driver-cores 1 --executor-memory 3g --executor-cores 2 --num-executors 40 --driver-java-options "-Denv=dev -Djna.library.path=$LD_LIBRARY_PATH:/usr/local/lib64 -Djava.library.path='$LD_LIBRARY_PATH:/usr/local/lib64 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" --conf spark.yarn.executor.memoryOverhead=5046 --conf spark.yarn.executor.memoryOverhead=5046 --conf spark.rdd.compress=true --conf spark.broadcast.compress=true --conf  "spark.executor.extraJavaOptions=-Djna.library.path=$LD_LIBRARY_PATH:/usr/local/lib64" --conf "spark.yarn.am.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf "spark.executor.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf "spark.driver.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib64" --conf spark.yarn.maxAppAttempts=4  --conf spark.yarn.am.attemptFailuresValidityInterval=1h target/job-ocr-pdf-1.0.jar
 

   ----------------KILL YARN APP----------  
	yarn application -kill application_XXXXXXXX_XXXX  
 
   ----------------TEST PURPOUSES---------- 
 spark-submit  --master local[*] --class "com.jene.cognitive.OcrTess4jSpark" --driver-memory 8g --driver-java-options "-Denv=dev -Djna.library.path=$LD_LIBRARY_PATH:/usr/local/lib64 -Djava.library.path='$LD_LIBRARY_PATH:/usr/local/lib64' -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" target/job-ocr-pdf-1.0.jar

   ----------------CLIENT MODE LESS STABLE ---------- 

spark-submit  --master yarn-client --class "com.jene.cognitive.OcrTess4jSpark" --driver-java-options "-Denv=dev -Djna.library.path='$LD_LIBRARY_PATH:/usr/local/lib64' -Djava.library.path=$LD_LIBRARY_PATH:/usr/local/lib64 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" --driver-memory 6g --num-executors 8 --executor-memory 6g --conf "spark.executor.extraJavaOptions=-Djna.library.path='$LD_LIBRARY_PATH:/usr/local/lib64'"  --conf "spark.executor.extraLibraryPath=/apps/opt/cloudera/parcels/CDH-5.11.1-1.cdh5.11.1.p0.4/lib/hadoop/lib/native:/usr/local/lib" target/job-ocr-pdf-1.0.jar
```
You have to check that the project properties set are:

```
path-pdfs=/data/ocr/pdfs (hdfs)

path-output=/apps/tmp/ocr_txtfiles/ (fs)
```



