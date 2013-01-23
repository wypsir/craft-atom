#! /bin/sh 

RP=/export/test/craft.atom.test
CP=$RP:$RP/cfg

for f in $RP/lib/*.jar; do
  CP=$CP:$f;
done

export CP

JAVA_OPTS='-Xms256m -Xmx256m -Dservice=netty-echo-server'
java $JAVA_OPTS -classpath $CP org.craft.atom.test.nio.NettyEchoServer