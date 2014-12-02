Start hadoop and yarn
=====================

start-dfs.sh
start-yarn.sh
jps

Stop everything
===============

stop-all.sh

Run your jar
============
cd into the jar directory
hadoop jar multimedia-indexing.jar gr.iti.mklab.reveal.mapreduce.VisualJob /input/imgs.txt /output
(where imgs.txt a list of image urls)

To fix datanode not starting
============================

stop-all.sh
rm -rf /tmp/hadoop-kandreadou
rm -rf /usr/local/hadoop_store/hdfs/datanode (no sudo)
rm -rf /usr/local/hadoop_store/hdfs/namenode (no sudo)
mkdir -p /usr/local/hadoop_store/hdfs/datanode (no sudo)
mkdir -p /usr/local/hadoop_store/hdfs/namenode (no sudo)
hadoop namenode -format
start-dfs.sh
start-yarn.sh
jps

Hadoop file commands
====================

hadoop dfs -put [your directory] /input
hadoop dfs -ls /output
hadoop dfs -cat /output/part-r-00000

mapred-site.xml configuration for local pseudodistributed mode
==============================================================

```
<configuration>
	<property>
   <name>mapreduce.framework.name</name>
   <value>yarn</value>
</property>
<property>
    <name>mapreduce.map.java.opts</name>
    <value>-Xmx3072m</value>
  </property>
  <property>
    <name>mapreduce.reduce.java.opts</name>
    <value>-Xmx3072m</value>
  </property>
  <property>
    <name>mapreduce.map.memory.mb</name>
    <value>4096</value>
  </property>
   <property>
    <name>mapreduce.reduce.memory.mb</name>
    <value>4096</value>
  </property>

</configuration>
```

Stackoverflow thread concerning heap space problem
==================================================

https://stackoverflow.com/questions/21005643/container-is-running-beyond-memory-limits


Amazon Elastic MapReduce Bootstrap Action
=========================================
Goal: Increase java heap space used by the containers running the JVMs for the mapper and reducer
Configure Hadoop
s3://elasticmapreduce/bootstrap-actions/configure-hadoop
-m, mapreduce.reduce.java.opts=-Xmx1900m, -m, mapreduce.map.java.opts=-Xmx1900m, -m, mapreduce.map.memory.mb=1900, -m, mapreduce.reduce.memory.mb=1900


Amazon Elastic MapReduce Custom Jar Step Configuration
======================================================

JAR location: s3://gr-mklab/multimedia-indexing.jar
Main class: None
Arguments: gr.iti.mklab.reveal.mapreduce.VisualJob s3://gr-mklab/yfcc_input9 s3://gr-mklab/output9 -Dfs.s3n.awsAccessKeyId=[your access key id] -Dfs.s3n.awsSecretAccessKey=[your secret access key]
Action on failure: Terminate cluster
