FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /nonexistent --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
