####
# This Dockerfile is used in order to build a container that runs the Quarkus application in JVM mode
#
# Before building the container image run:
#
# ./gradlew assemble -PhaDiscoveryDir=../homeassistant-discovery
#
# Then, build the image with:
#
# docker build -f Dockerfile -t qbus-interface .
#
# Then run the container using:
#
# docker run -i --rm -p 8096:8096 qbus-interface
#
###
FROM registry.access.redhat.com/ubi9/openjdk-25

ENV LANGUAGE='en_US:en'


# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 qbusbridge/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 qbusbridge/build/quarkus-app/*.jar /deployments/
COPY --chown=185 qbusbridge/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 qbusbridge/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8096
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Xmx16m --sun-misc-unsafe-memory-access=allow"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
