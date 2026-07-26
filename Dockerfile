FROM gradle:jdk25 AS build
WORKDIR /app

ENV GRADLE_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"

COPY build.gradle settings.gradle ./
RUN --mount=type=cache,target=/home/gradle/.gradle/caches,sharing=locked \
    gradle dependencies --no-daemon

COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle/caches,sharing=locked \
    gradle bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS extractor
WORKDIR /app

COPY --from=build /app/build/libs/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring --no-create-home spring
USER spring:spring

COPY --from=extractor /app/extracted/dependencies/ ./
COPY --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/ ./

EXPOSE 8085

ENTRYPOINT [ \
    "java", \
    "-XX:+UseZGC", \
    "-XX:TieredStopAtLevel=1", \
    "-jar", "app.jar" \
]